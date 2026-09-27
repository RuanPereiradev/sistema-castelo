# Task 3.2 — Comanda: fechamento, taxa de serviço e divisão de conta

**Objetivo:** a comanda passa de `OPEN` a `CLOSING` com o total congelado
(subtotal + `serviceCharge`) lançado como um único `TabCharge` num folio `TAB`.
Recebe N pagamentos parciais em qualquer meio manual, mostra a divisão por
pessoas ou por item, volta a `OPEN` por override do garçom (`reopen`) estornando
o lançamento, e fecha (`CLOSED`) com saldo zero, fechando o folio na mesma
transação.

Decisões: `docs/decisions/task-3.2.md` (G1–G5 e F1–F15, aprovadas pelo Ruan em
2026-09-26, e as do DEV). Herdadas:

- 1.2 #8: o adicional segue o item na taxa.
- 1.3 #1: folio `TAB` recusa pagamento acima do saldo; troco é físico.
- 1.3 #3 + G4: o garçom (`WAITER`) recebe pelo fechamento; o caixa do restaurante e do self-service usa esse perfil.
- 1.3 #5, #16, #19: idempotência pelo header `Idempotency-Key`, opcional na rota e recusado pelo domínio.
- 1.3 #6: `ROOM_ACCOUNT` recusado no pagamento manual.
- 1.3 #7, #22: estorno só pelo valor integral; folio `TAB` pode ficar negativo por estorno.
- 1.3 #8, #11: o fechamento da comanda fecha o folio na mesma transação.
- 1.3 #26: a trava do folio tira do contexto a instância já lida.
- 1.4 #1: nenhum fluxo da v1 chama `TaxInvoiceIssuer` nem `PaymentProcessor`.
- 2.2 #1: grupos na mesma mesa se resolvem com divisão.
- 2.2 #8: mesa em `CLOSING` não desativa.
- 2.2 #15, #18: `FOR KEY SHARE` contra `FOR UPDATE`.
- 2.2 #16: `serviceCharge()` e `total()` nascem aqui.
- Limitação da 2.2: teste de concorrência cancelar comanda × lançar.

Gabarito: `TabService`/`TabController` (2.2) e `FolioService` (1.3).

Risco alto: cálculo monetário (taxa, rateio, centavo), transição de estado e
concorrência no fechamento. Teste denso nesses três pontos.

---

## 1. Escopo

**Dentro**
- `TabStatus`: `OPEN → CLOSING` (`startClosing`), `CLOSING → OPEN` (`reopen`) e `CLOSING → CLOSED` (`close`).
- Taxa de serviço:
  - percentual lido do `Setting` `restaurant.service-charge-percent` (padrão 10,00) e congelado em `startClosing` (F1);
  - desligar e religar na comanda inteira (`serviceChargeApplied`) e item a item (`serviceChargeWaived`), só em `OPEN`, sem motivo (F2), só o que nasceu com taxa (F3).
- Valores calculados: `serviceChargeBase()`, `serviceCharge(rate)`, `total(rate)`, `bill(rate)`.
- Divisão (F5):
  - igual em N partes, sobre o total, sobre o total de um grupo e sobre o saldo (cálculo, não gravado);
  - por item: `split_group` gravado por item, com subtotal, taxa e total por grupo;
  - `guestCount` opcional (F8).
- Pagamento pela comanda: `POST /tabs/{id}/payments` com `Idempotency-Key`, pelo `FolioFacade.receivePayment` (G2).
- Ligação comanda → folio: `tab.folio_id` e `tab.tab_charge_id`; folio `TAB` aberto no primeiro `startClosing` e **reaproveitado** depois do `reopen`.
- Cancelar comanda reaberta (com folio): fecha o folio junto; com saldo ≠ 0, `FOLIO_BALANCE_NOT_ZERO` (D21: o `TabService.cancel` passa a usar a porta).
- `V9__tab_closing.sql` com `seed` do `Setting`; o `DevUserSeeder` grava a chave ao criar a propriedade (G3).
- §11.1 e §13 do schema corrigidos para um único `TabCharge` (F6).
- `http/34-restaurant-tab-closing.http` e a linha dele no `http/README.md`.
- Testes de integração, com o de concorrência herdado da 2.2.

**Fora**
- Destino `ROOM_ACCOUNT`, ponte para o folio do hóspede: 3.3. A coluna `destination` já aceita os dois valores; a 3.2 só grava `DIRECT_PAYMENT`.
- KDS e transições de `TabItemStatus`: 3.5. O KDS continua avançando item em comanda `CLOSING`/`CLOSED` (F12): a 3.2 não bloqueia.
- Transferência e junção: 3.6.
- Nota fiscal (1.4 #1), `PaymentIntent`/QR e o job que devolve `CLOSING → OPEN` (v1.1).
- Turno de caixa: 2.4. A 3.2 herda o que o `FolioService.receivePayment` faz com `CASH` (C2): com o caixa obrigatório e sem turno, `CASH_DRAWER_SESSION_NOT_OPEN`.
- Desconto: `AdjustmentCharge` do `ADMIN` na rota da 1.3 (F15). Gorjeta além da taxa (F14). Reabrir `CLOSED` (F11). "Grupo já pagou" (F7). Eventos de domínio.

---

## 2. Modelo de dados — `V9__tab_closing.sql`

Antes, o §11.1 do schema passa a descrever esta migration e o §13 perde a linha
"soma dos `Charge` de um `split_group`" (F6: um único `TabCharge` por comanda).

```sql
-- Task 3.2 - tab closing: service charge, split bill and the link to the folio.
-- destination accepts ROOM_ACCOUNT already; only task 3.3 writes it.

ALTER TABLE tab
    ADD COLUMN folio_id               UUID         REFERENCES folio(id),
    ADD COLUMN tab_charge_id          UUID         REFERENCES charge(id),
    ADD COLUMN destination            VARCHAR(20)
                                      CHECK (destination IN ('DIRECT_PAYMENT','ROOM_ACCOUNT')),
    ADD COLUMN service_charge_applied BOOLEAN      NOT NULL DEFAULT TRUE,
    ADD COLUMN service_charge_rate    NUMERIC(5,4) CHECK (service_charge_rate >= 0),
    ADD COLUMN guest_count            SMALLINT     CHECK (guest_count BETWEEN 1 AND 999),
    ADD COLUMN closing_started_at     TIMESTAMPTZ,
    ADD COLUMN closing_started_by     UUID,
    ADD COLUMN closed_at              TIMESTAMPTZ,
    ADD COLUMN closed_by              UUID;

-- self-service tabs never carried the charge
UPDATE tab SET service_charge_applied = (origin = 'TABLE_SERVICE');

ALTER TABLE tab
    ADD CONSTRAINT ck_tab_closing CHECK (
        status NOT IN ('CLOSING','CLOSED')
        OR (closing_started_at IS NOT NULL AND closing_started_by IS NOT NULL
            AND service_charge_rate IS NOT NULL AND folio_id IS NOT NULL
            AND tab_charge_id IS NOT NULL)
    ),
    ADD CONSTRAINT ck_tab_closed CHECK (
        status <> 'CLOSED'
        OR (closed_at IS NOT NULL AND closed_by IS NOT NULL AND destination IS NOT NULL)
    );

ALTER TABLE tab_item
    ADD COLUMN split_group           SMALLINT NOT NULL DEFAULT 1 CHECK (split_group BETWEEN 1 AND 99),
    ADD COLUMN service_charge_waived BOOLEAN  NOT NULL DEFAULT FALSE,
    ADD CONSTRAINT ck_tab_item_service_charge_waived
        CHECK (NOT service_charge_waived OR service_chargeable);

CREATE INDEX idx_tab_folio ON tab (folio_id) WHERE folio_id IS NOT NULL;

INSERT INTO setting (id, property_id, setting_key, setting_value, value_type, description)
SELECT gen_random_uuid(), p.id, 'restaurant.service-charge-percent', '10.00', 'DECIMAL',
       'Service charge on table-service tabs, in percent points'
  FROM property p
ON CONFLICT (property_id, setting_key) DO NOTHING;
```

Notas:
- `service_chargeable` continua sendo o retrato imutável da elegibilidade (2.2). O que o operador desliga vai em `service_charge_waived`, e dá para religar.
- `tab_charge_id` é o `TabCharge` **em vigor**. `reopen` o zera; o próximo `startClosing` aponta para o novo lançamento. O anterior e o seu estorno ficam no folio.
- `folio_id` sem índice único: na 3.3 várias comandas apontam para o mesmo folio `STAY`.
- Banco vazio em `dev`: a `property` nasce depois do Flyway, no `DevUserSeeder`, que grava a chave junto (G3). No teste de integração, a chave é gravada pela `SettingRepository`. A leitura ausente responde `SETTING_NOT_FOUND` 404, sem valor padrão escondido.

---

## 3. Invariantes

**Taxa de serviço**
1. Só `OPEN` aceita mudança de taxa: `TabStatus.acceptsServiceChargeChange()`, senão `TAB_NOT_OPEN`.
2. `removeServiceCharge()` desliga na comanda; `restoreServiceCharge()` religa **só o que a origem cobra**: em `SELF_SERVICE` fica desligada e nada muda (F3). Repetir o estado é aceito em silêncio.
3. `removeServiceChargeFrom(itemId)` / `restoreServiceChargeTo(itemId)`, nesta ordem de checagem:
   1. status (`TAB_NOT_OPEN`);
   2. item existe (`TAB_ITEM_NOT_FOUND`);
   3. item não `serviceChargeable`: `TAB_ITEM_NOT_SERVICE_CHARGEABLE` (422);
   4. item cancelado: aceito em silêncio, **sem mudar nada** (não muda dinheiro);
   5. repetir o mesmo estado: aceito em silêncio.
4. `TabItem.countsForServiceCharge()` = ativo && `serviceChargeable` && !`serviceChargeWaived`.
   `serviceChargeBase()` = Σ `lineTotal` dos itens que contam, se `serviceChargeApplied`; senão zero.
   - O adicional já está no `lineTotal`, então segue o item (1.2 #8). Item por peso segue a regra 13 da 2.2.
5. `serviceCharge(currentRate)` = `serviceChargeBase().percentage(rate)`, **uma vez sobre a soma**, HALF_UP no centavo. Nunca item a item.
   - `rate` = o percentual congelado, se houver (`CLOSING`/`CLOSED`); senão `currentRate`.
   - `currentRate` é obrigatório (`NullPointerException`), inclusive quando há congelado.
6. `total(currentRate)` = `subtotal()` + `serviceCharge(currentRate)`.

**Divisão**
7. `assignToSplitGroup(Map<TabItemId, Integer>)` em `OPEN` e `CLOSING` (`acceptsSplitChange()`, senão `TAB_NOT_OPEN`). Não muda o total nem o lançamento. Tudo ou nada:
   1. status;
   2. **todos** os grupos de 1 a 99 (`INVALID_SPLIT_GROUP`; nulo também);
   3. **todos** os itens existem (`TAB_ITEM_NOT_FOUND`);
   4. aplica. A linha vai inteira para o grupo (F5). Item cancelado pode ser movido (não pesa em nada). Mapa vazio: nada muda.
8. `TabBill.groups()`: um `TabBill.SplitGroup` por grupo que tenha **item ativo**, em ordem crescente de grupo. Por grupo g:
   - `subtotal_g` = Σ `lineTotal` ativos do grupo;
   - `base_g` = Σ `lineTotal` dos itens do grupo que contam para a taxa (zero se a comanda está sem taxa);
   - `serviceCharge_g` = **rateio pelo maior resto** do `serviceCharge()` da comanda, proporcional a `base_g`: cada grupo leva o piso de `S × base_g / B` em centavos; os centavos que sobram vão, um a um, aos maiores restos, e no empate ao menor número de grupo (F4). Σ `serviceCharge_g` = `serviceCharge()` sempre;
   - `total_g` = `subtotal_g` + `serviceCharge_g`; Σ `total_g` = `total()`.
9. Divisão igual `TabBill.evenSplit(Money amount, int parts)`:
   - `q = cents / N`, `r = cents % N`: as **r primeiras** cotas levam `q + 0,01` (F4). Σ = `amount`; duas cotas diferem em no máximo 0,01;
   - N de 1 a 99, e N ≤ centavos de `amount` (nenhuma cota zero): senão `INVALID_SPLIT_PARTS`. `amount` zero ou negativo com qualquer N: `INVALID_SPLIT_PARTS`.
   - `TabBill.evenSplit(parts)` = sobre `total()`; `TabBill.SplitGroup.evenSplit(parts)` = sobre `total_g` (F5, grupo redividido); `Tab.evenSplit(currentRate, parts)` = `bill(currentRate).evenSplit(parts)`.
   - `TabBill.acceptsEvenSplit(Money amount, int parts)` diz se a divisão é possível, sem lançar.
10. `recordGuestCount(n)` em `OPEN` e `CLOSING` (`acceptsSplitChange()`, senão `TAB_NOT_OPEN`), de 1 a 999 (`INVALID_GUEST_COUNT`).

**Fechamento**
11. `startClosing(currentRate, billing, by, at)`, nesta ordem:
    1. `acceptsClosing()` (`TAB_NOT_OPEN`);
    2. algum item ativo (`TAB_HAS_NO_ACTIVE_ITEMS`, 409);
    3. congela `currentRate`;
    4. abre o folio `TAB` se `folio_id` é nulo (`billing.openFolio`), senão reaproveita;
    5. `billing.charge(folio, id, total, descrição)`; descrição `"Tab card <n>"` ou `"Tab table <diningTableId>"`;
    6. grava `tab_charge_id`, `closing_started_*` e `CLOSING`.
12. `CLOSING` recusa: lançar item, cancelar item, cancelar a comanda (`TAB_NOT_OPEN`) e mudar a taxa. Aceita: pagamento, grupos, `guestCount`, leitura e o KDS (F12). Mesa e cartão seguem ocupados (`holdsItsPlace`).
13. `reopen(reason, billing)`, nesta ordem:
    1. `acceptsReopening()` (`TAB_NOT_CLOSING`);
    2. motivo aparado de 1 a 500 (`INVALID_REOPENING_REASON`);
    3. `billing.reverse(folio, tab_charge_id, motivo)` (1.3 #7);
    4. zera `tab_charge_id`, o percentual congelado e `closing_started_*`; volta a `OPEN`.
    - Pagamento registrado fica no folio como crédito (F10); o folio pode ficar negativo (1.3 #22).
    - O rastro da reabertura é o estorno no folio: motivo, autor (`created_by`) e instante.
14. `receivePayment(method, amount, key, billing)`:
    - `acceptsPayment()`, verdadeiro em `CLOSING` e `CLOSED`, senão `TAB_NOT_CLOSING`;
    - em `CLOSED`, delega do mesmo jeito: o folio devolve o replay idempotente ou `FOLIO_CLOSED`;
    - o resto é regra do `billing`: chave, método manual, valor > 0, não passar do saldo e o turno de caixa (2.4).
15. `close(billing, by, at)`:
    1. `acceptsSettlement()`, só `CLOSING` (`TAB_NOT_CLOSING`);
    2. `billing.closeFolio(folio)`, que recusa saldo ≠ 0 com `FOLIO_BALANCE_NOT_ZERO` (o código do billing, sem duplicar) — na mesma transação (1.3 #11);
    3. `destination = DIRECT_PAYMENT`, `closed_*`, `CLOSED`. Libera mesa e cartão.
    - Fecha com item ainda `PENDING`/`IN_PREPARATION` (F12).
16. `CLOSED` é final na v1 (F11).
17. `cancel(reason, billing, by, at)`: as regras do `cancel` da 2.2 (status → item ativo → motivo) e, se há `folio_id` (comanda reaberta), `billing.closeFolio(folio)` antes de ir a `CANCELLED`; com saldo ≠ 0, `FOLIO_BALANCE_NOT_ZERO`. A rota `POST /tabs/{id}/cancel` (`TabService.cancel`) usa esta forma (D21). O `cancel(reason, by, at)` da 2.2 fica para comanda sem folio, sem caminho de produção que o chame.

**Folio da comanda no balcão (R1, decisão do Ruan, 2026-09-27)**
19. Pelas rotas do `FolioController`, o folio cujo dono é `OwnerType.TAB` recusa **estorno de lançamento** e **fechamento** com `FOLIO_OWNED_BY_TAB` (409), para todos os perfis. Quem estorna e fecha é a comanda (`reopen`, `close`, `cancel`) pela fachada. Pagamento, estorno de pagamento (`refund`) e ajuste do `ADMIN` continuam liberados. A regra mora em `Folio.requireManagedByCounter()`, chamada só pelo caminho das rotas.

**Porta**
18. `TabBilling`, interface em `restaurant.domain`, implementada em `restaurant.infra` sobre o `FolioFacade`:
    - `FolioId openFolio(TabId)`;
    - `ChargeId charge(FolioId, TabId, Money, String description)`, com a comanda como origem do lançamento (`ChargeSource.tab`);
    - `void reverse(FolioId, ChargeId, String reason)`;
    - `ReceivedPaymentView receivePayment(FolioId, PaymentMethod, Money, String idempotencyKey)`;
    - `Money balanceOf(FolioId)`;
    - `Money paidOn(FolioId)` = Σ lançamentos − saldo;
    - `void closeFolio(FolioId)`, que recusa saldo ≠ 0 com o código do billing.

    O "reaproveita ou abre" fica no agregado; o teste de unidade usa um fake.

---

## 4. Assinaturas públicas

```java
// Tab — novo (bloco "closing")
void removeServiceCharge();                   void restoreServiceCharge();
void removeServiceChargeFrom(TabItemId id);   void restoreServiceChargeTo(TabItemId id);
void assignToSplitGroup(Map<TabItemId, Integer> assignments);
void recordGuestCount(int guestCount);
void startClosing(Percentage currentRate, TabBilling billing, UUID by, Instant at);
ReceivedPaymentView receivePayment(PaymentMethod method, Money amount, String idempotencyKey, TabBilling billing);
void reopen(String reason, TabBilling billing);
void close(TabBilling billing, UUID by, Instant at);
void cancel(String reason, TabBilling billing, UUID by, Instant at);

Money serviceChargeBase();
Money serviceCharge(Percentage currentRate);
Money total(Percentage currentRate);
TabBill bill(Percentage currentRate);
List<Money> evenSplit(Percentage currentRate, int parts);

boolean serviceChargeApplied(); Optional<Percentage> serviceChargeRate();
Optional<Integer> guestCount(); Optional<FolioId> folioId(); Optional<ChargeId> tabChargeId();
Optional<TabDestination> destination(); Optional<Instant> closingStartedAt(); Optional<UUID> closingStartedBy();
Optional<Instant> closedAt(); Optional<UUID> closedBy();

// TabItem — novo
boolean serviceChargeWaived(); int splitGroup(); boolean countsForServiceCharge();

// TabStatus — novo
acceptsServiceChargeChange() (OPEN) · acceptsClosing() (OPEN) · acceptsReopening() (CLOSING)
acceptsPayment() (CLOSING, CLOSED) · acceptsSettlement() (CLOSING) · acceptsSplitChange() (OPEN, CLOSING)

// TabDestination: DIRECT_PAYMENT · ROOM_ACCOUNT

// TabBill (record)
record TabBill(Money subtotal, Money serviceChargeBase, Percentage serviceChargeRate,
               Money serviceCharge, Money total, List<SplitGroup> groups) {
    List<Money> evenSplit(int parts);
    SplitGroup group(int splitGroup);                       // INVALID_SPLIT_GROUP se não houver
    static List<Money> evenSplit(Money amount, int parts);
    static boolean acceptsEvenSplit(Money amount, int parts);
    record SplitGroup(int splitGroup, Money subtotal, Money serviceChargeBase, Money serviceCharge,
                      Money total, List<TabItemId> itemIds) {
        List<Money> evenSplit(int parts);
    }
}
```

**Serviço e acesso a dados**
- `TabClosingService` (classe nova, para não disputar o `TabService` com a 3.5): `removeServiceCharge`, `restoreServiceCharge`, `removeServiceChargeFrom`, `restoreServiceChargeTo`, `assignSplitGroups`, `recordGuestCount`, `bill`, `startClosing`, `receivePayment`, `reopen`, `close`, `currentServiceChargeRate`. O cancelamento da comanda continua no `TabService` (D21).
- Travas, sempre comanda → folio:
  - `FOR UPDATE` (`findByIdForStatusChange`, já existe): `startClosing`, `reopen`, `close`, `cancel`;
  - `FOR KEY SHARE` (`findByIdForItemEntry`, já existe): taxa, grupos, `guestCount`, pagamento.
- `Tab` e `TabItem` com `@DynamicUpdate`: quem escreve sob `FOR KEY SHARE` (grupo, taxa do item, KDS da 3.5, cancelamento de item) só grava as colunas que mudou, e um não desfaz o outro (decisão #D9).
- O percentual vem de `Settings.asPercentage("restaurant.service-charge-percent")`.

---

## 5. Códigos de erro

| Código | HTTP | Quando |
|---|---|---|
| `TAB_HAS_NO_ACTIVE_ITEMS` | 409 | Iniciar fechamento sem item ativo |
| `TAB_NOT_CLOSING` | 409 | Pagar numa comanda que não está `CLOSING`/`CLOSED`; reabrir ou fechar fora de `CLOSING` |
| `TAB_ITEM_NOT_SERVICE_CHARGEABLE` | 422 | Tirar ou pôr taxa em item que nunca a teve |
| `INVALID_SPLIT_GROUP` | 422 | Grupo fora de 1 a 99; `splitGroup` pedido sem item ativo |
| `INVALID_SPLIT_PARTS` | 422 | Partes fora de 1 a 99, ou mais partes que centavos |
| `INVALID_GUEST_COUNT` | 422 | Pessoas fora de 1 a 999 |
| `INVALID_REOPENING_REASON` | 422 | Motivo de reabertura ausente, em branco ou acima de 500 |
| `FOLIO_OWNED_BY_TAB` | 409 | Estornar lançamento ou fechar, pelas rotas do billing, o folio de uma comanda — qualquer perfil, `ADMIN` inclusive (R1) |

Reaproveitados: `TAB_NOT_FOUND`, `TAB_ITEM_NOT_FOUND`, `TAB_NOT_OPEN` (também
para grupo e `guestCount` em `CLOSED`/`CANCELLED`/`MERGED`, #D8); do `billing`:
`FOLIO_BALANCE_NOT_ZERO`, `FOLIO_CLOSED`, `PAYMENT_EXCEEDS_BALANCE`,
`INVALID_PAYMENT_AMOUNT`, `PAYMENT_METHOD_NOT_ACCEPTED`, `INVALID_IDEMPOTENCY_KEY`,
`IDEMPOTENCY_KEY_REUSED`, `INVALID_CHARGE_AMOUNT` (total zero, #D16) e o código
de caixa da 2.4; `SETTING_NOT_FOUND`; `INVALID_MONEY`.

---

## 6. API

`ADMIN` e `WAITER` em todas as rotas; `KITCHEN` e `FRONT_DESK` recebem 403 (G4).

```
PUT  /api/restaurant/tabs/{tabId}/service-charge                 {applied}                            200 TabBillResponse
PUT  /api/restaurant/tabs/{tabId}/items/{itemId}/service-charge  {applied}                            200 TabBillResponse
PUT  /api/restaurant/tabs/{tabId}/guest-count                    {guestCount}                         200 TabBillResponse
PUT  /api/restaurant/tabs/{tabId}/split-groups                   {assignments:[{itemId, splitGroup}]} 200 TabBillResponse
GET  /api/restaurant/tabs/{tabId}/bill?parts=&splitGroup=                                              200 TabBillResponse
POST /api/restaurant/tabs/{tabId}/closing                                                              200 TabBillResponse
POST /api/restaurant/tabs/{tabId}/payments   Idempotency-Key     {method, amount}                     201 TabPaymentResponse
POST /api/restaurant/tabs/{tabId}/reopen                         {reason}                             200 TabResponse
POST /api/restaurant/tabs/{tabId}/close                                                                200 TabResponse
```

- Diverge da §8 do plano (um `POST .../close` só): o fechamento tem três passos — pré-conta, pagamentos, fecha (F9).
- **`TabBillResponse`:** `{tabId, status, guestCount, serviceChargeApplied, subtotal, serviceChargeBase, serviceChargeRate ("10.00", em pontos percentuais), serviceCharge, total, paid, balance, groups:[{splitGroup, subtotal, serviceChargeBase, serviceCharge, total, itemIds}], evenSplitParts, evenSplitGroup, evenSplit, balanceEvenSplit}`.
  - `evenSplitParts`: as partes usadas (pedidas ou o `guestCount`), `null` sem divisão; `evenSplitGroup`: o grupo redividido, `null` para a comanda inteira.
  - A regra da divisão padrão mora em `TabClosingView.evenSplit(parts, splitGroup)`, não no DTO (D11).
  - `paid` e `balance` são `null` enquanto não há folio.
  - `evenSplit`: com `parts`, divide o total (ou o total do grupo, com `splitGroup`), e parte inválida é 422; sem `parts`, usa `guestCount` quando a divisão é possível, senão `null` (#D11).
  - `balanceEvenSplit`: a mesma divisão sobre o saldo, quando há folio, o saldo é positivo e a divisão é possível; senão `null`. Não se aplica com `splitGroup`.
- **`TabPaymentResponse`:** `{paymentId, method, amount, paidAt, bill: TabBillResponse}`. Replay: 201, o pagamento original e a pré-conta atual.
- **`TabResponse` ganha:** `serviceChargeApplied, serviceChargeRate, serviceCharge, total, guestCount, folioId, closingStartedAt, closingStartedBy, closedAt, closedBy, destination`. `serviceChargeRate`, `serviceCharge` e `total` vêm **ao vivo** em `OPEN` (percentual atual do `Setting`) e **congelados** depois (D20). Toda leitura de comanda passa a ler o `Setting`: sem a chave, `SETTING_NOT_FOUND`.
- **Item ganha:** `serviceChargeWaived, splitGroup`. **Lista ganha:** `total` (ao vivo em `OPEN`, congelado depois).
- Corpos sem bean validation para o que o domínio recusa; `applied` ausente, `method` ausente ou fora do enum: 400. `guestCount` ausente = 0 → `INVALID_GUEST_COUNT`. `splitGroup` ausente = `INVALID_SPLIT_GROUP`; `itemId` ausente = 400.

---

## 7. Concorrência

- `startClosing`, `reopen`, `close` e `cancel` usam `FOR UPDATE` na comanda: esperam lançamentos, cancelamentos, pagamentos e mudanças de taxa/grupo em curso (`FOR KEY SHARE`), e estes esperam por eles. O total lançado é sempre o dos itens comitados.
- Pagamentos usam `FOR KEY SHARE`: dois garçons recebendo ao mesmo tempo não se bloqueiam na comanda e se serializam na trava do folio (1.3 #15).
- A rota do `billing` (`FRONT_DESK`) trava só o folio: não há ciclo de travas.

---

## 8. Testes

**Unidade (TEST, agente separado), densa**, com um `TabBilling` fake:
- *Taxa:* base 100,00 → 10,00; 33,35 → 3,34 (3,335 HALF_UP); 0,05 → 0,01; 0,04 → 0,00; item cancelado fora; item com adicional; item inelegível; item com a taxa tirada e restaurada; comanda com a taxa desligada; `SELF_SERVICE` sem taxa (`shouldNotApplyServiceChargeOnSelfServiceTab`); 3 × 3,35 a 10% dá 1,01 (não 1,02); percentual congelado em `CLOSING` e ignorado o `currentRate`.
- *Rateio:* 100,00 / 3 → 33,34 + 33,33 + 33,33; 10,00 / 4; 0,03 / 3; 0,02 / 3 recusado; N = 1; N = 99; N = 100 recusado; Σ = total.
- *Grupos:* dois grupos de base 10,05 a 10% → 2,01 repartido em 1,01 + 1,00; três grupos com restos diferentes; grupo sem item elegível; item cancelado fora do grupo; Σ `total_g` = `total()`.
- *Transições:* matriz `TabStatus` × (taxa, grupo, pessoas, iniciar fechamento, pagar, reabrir, fechar, cancelar, lançar, cancelar item); ordem de `startClosing`; reabrir estorna o lançamento em vigor; segundo `startClosing` reaproveita o folio e lança de novo; `close` com saldo ≠ 0 recusado; `cancel` com folio fecha o folio, ou recusa com saldo ≠ 0.

**Integração (DEV), no `app`:**
- *Fatia:* abre mesa → lança 3 itens → tira a taxa de um → grupos 1/2 → `bill?parts=3` → `closing` → paga PIX parcial → paga cartão o resto → `close`. O folio fecha, a mesa aceita nova comanda, o replay do pagamento devolve o mesmo pagamento.
- *Reabertura:* `closing` → paga 10,00 → `reopen` → lança sobremesa → `closing` → saldo = novo total − 10,00.
- `KITCHEN` recebe 403.
- **Concorrência (Testcontainers):** (a) 5 lançamentos + 1 `startClosing`: o `TabCharge` = total final, todo lançamento é 201 ou `TAB_NOT_OPEN`; (b) 3 `startClosing`: um 200 e dois `TAB_NOT_OPEN`, um folio, um lançamento; (c) cancelar comanda × lançar (herdado da 2.2): nunca `CANCELLED` com item ativo; (d) `reopen` × pagamento: saldo coerente, nenhum 500.

---

## 9. `.http`

`http/34-restaurant-tab-closing.http`.
- **Preparação:** logins, mesa aleatória, itens (um com `serviceChargeEligible = false`), cartão aleatório.
- **Caminho feliz:** o da fatia, mais a reabertura.
- **Negativos:** um por código da seção 5, mais `PAYMENT_EXCEEDS_BALANCE`, `PAYMENT_METHOD_NOT_ACCEPTED` (`ROOM_ACCOUNT`), `IDEMPOTENCY_KEY_REUSED`, `FOLIO_BALANCE_NOT_ZERO` no `close`, lançar em `CLOSING` (`TAB_NOT_OPEN`), desativar mesa em `CLOSING` (`DINING_TABLE_HAS_OPEN_TAB`), 401 e o 403 da cozinha.
- Pagamentos em PIX, cartão e `CASH` (o arquivo abre e fecha o próprio turno de caixa).
- No `40`: fechar e estornar o folio de comanda pelo balcão → `FOLIO_OWNED_BY_TAB` (R1).
- **Cancelar comanda reaberta:** com crédito no folio → `FOLIO_BALANCE_NOT_ZERO`; depois do `refund` pelo `ADMIN` → `CANCELLED` com o folio fechado.
- **No fim:** fecha ou cancela o que ficou aberto.
- A limitação da 1.3 (`FOLIO_ALREADY_OPENED_FOR_OWNER` sem cenário) **continua**: com o folio reaproveitado, a rota da comanda nunca produz esse erro.

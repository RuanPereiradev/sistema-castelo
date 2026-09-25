# Task 1.3 — Folio: lançamentos, estorno e pagamentos

**Objetivo:** a conta (`Folio`) recebe lançamentos, estornos, ajustes e
pagamentos, sabe o próprio saldo e só fecha quando o saldo é zero.

Decisões: `docs/decisions/task-1.3.md`. Contrato entre módulos: o `billing/api`
congelado na 0.6 (`FolioFacade`, `FolioView`, `ChargeRequest`, `FolioOwner`,
`FolioReference`; decisões #1 a #14 da 0.6), **com os três acréscimos aprovados
pelo Ruan nesta task (#9, #10, #11)**. Gabarito de estilo: o módulo `restaurant`
(`MenuItem` para agregado com filhos, `DiningTableService` e
`DiningTableController` para caso de uso e rotas).

Aqui é dinheiro. Teste **denso** em saldo, estorno, pagamento e fechamento.

---

## 1. Escopo

**Dentro**
- Agregado `Folio` com `Charge` (`RoomNightCharge`, `TabCharge`,
  `AdjustmentCharge`) e `Payment`
- Implementação do `FolioFacade` inteiro, incluindo `openStayFolio`,
  `changeReference` e o novo `close`. Nenhum hotel chama ainda
- Acréscimos ao `billing/api` (#9, #10, #11)
- Rotas REST de leitura, pagamento, estorno de pagamento, ajuste, estorno de
  lançamento e fechamento; rota só de `dev` para abrir folio e lançar (#12)
- Migration `V6__billing.sql`: `folio`, `charge`, `payment`, `payment_intent`
- Atualizar a seção 7 de `docs/schema-banco-de-dados.md` para a V6 real
  **antes** de escrever a migration
- `http/40-billing-folios.http`

**Fora**
- `CashDrawerSession` e `cash_movement`: task 2.4. A coluna
  `payment.cash_drawer_session_id` nasce na V6, nula, **sem FK** e **sem
  mapeamento JPA**. A 2.4 mapeia, cria a FK (já prevista na V8) e decide se
  pagamento em `CASH` exige turno aberto
- `PaymentIntent` (QR code, sinal pelo portal): v1.1. A **tabela** nasce na V6,
  como o schema reserva. Sem entidade, repositório nem rota.
  `payment.payment_intent_id` fica sempre nulo e não é mapeado
- Ligação comanda → folio (`tab.folio_id`, `openTabFolio` chamado no fechamento
  da comanda): task 3.2
- `TabCharge` no folio do hóspede: 3.3. Check-out consolidado: 3.4
- Reabrir folio fechado: não existe na v1. Eventos de domínio: nenhum (#7 da 0.6)
- `http/README.md`, `CLAUDE.md`, `docs/MIGRATIONS.md`: quem edita é o orquestrador

---

## 2. Modelo de dados

`V6__billing.sql`, a partir de `docs/schema-banco-de-dados.md` §7, com estas
mudanças (atualize o documento primeiro):

| Tabela | Mudança | Por quê |
|---|---|---|
| `folio` | `+ owner_id UUID NOT NULL`, único por `(folio_type, owner_id)` | O `FolioView` devolve o `FolioOwner` (#1 da 0.6). `STAY` ↔ `RESERVATION`, `TAB` ↔ `TAB` |
| `folio` | `CHECK`: folio `STAY` tem `reference_code` e `reference_label` | `openStayFolio` sempre recebe referência |
| `folio` | `idx_folio_open_ref` passa a ser `UNIQUE` (#17) | Duas contas abertas com o mesmo código deixariam `findOpenStayFolioByCode` ambíguo |
| `charge` | `posted_at`/`posted_by` trocados pelas quatro colunas de auditoria | Autor = `created_by` (#9 da 0.6); auditoria em toda tabela transacional |
| `charge` | `+ reversal_of_charge_id UUID REFERENCES charge(id)`, `UNIQUE` | `ChargeView.reversalOf`; um só estorno por lançamento |
| `charge` | `CHECK (amount <> 0)`; estorno exige `reason` | |
| `payment` | `+` auditoria, `+ refunded_at`, `refunded_by`, `refund_reason` | Estorno de pagamento (#2) |

```sql
CREATE TABLE folio (
    id              UUID PRIMARY KEY,
    property_id     UUID         NOT NULL REFERENCES property(id),
    folio_type      VARCHAR(10)  NOT NULL CHECK (folio_type IN ('STAY','TAB')),
    owner_id        UUID         NOT NULL,
    status          VARCHAR(10)  NOT NULL DEFAULT 'OPEN'
                    CHECK (status IN ('OPEN','CLOSED')),
    reference_code  VARCHAR(20),
    reference_label VARCHAR(150),
    opened_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    closed_at       TIMESTAMPTZ,
    closed_by       UUID,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      UUID,
    updated_at      TIMESTAMPTZ,
    updated_by      UUID,
    CONSTRAINT uk_folio_owner UNIQUE (folio_type, owner_id),
    CONSTRAINT ck_folio_closed CHECK (
        status <> 'CLOSED' OR (closed_at IS NOT NULL AND closed_by IS NOT NULL)
    ),
    CONSTRAINT ck_folio_stay_reference CHECK (
        folio_type <> 'STAY'
        OR (reference_code IS NOT NULL AND reference_label IS NOT NULL)
    )
);

CREATE TABLE charge (
    id                     UUID PRIMARY KEY,
    folio_id               UUID          NOT NULL REFERENCES folio(id),
    charge_type            VARCHAR(20)   NOT NULL
                           CHECK (charge_type IN ('ROOM_NIGHT','TAB','ADJUSTMENT')),
    amount                 NUMERIC(12,2) NOT NULL CHECK (amount <> 0),
    description            VARCHAR(255)  NOT NULL,
    source_id              UUID,
    reference_date         DATE,
    authorized_by          UUID,
    reason                 TEXT,
    reversal_of_charge_id  UUID          REFERENCES charge(id),
    created_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by             UUID,
    updated_at             TIMESTAMPTZ,
    updated_by             UUID,
    CONSTRAINT uk_charge_reversal UNIQUE (reversal_of_charge_id),
    CONSTRAINT ck_charge_adjustment CHECK (
        charge_type <> 'ADJUSTMENT'
        OR (authorized_by IS NOT NULL AND reason IS NOT NULL)
    ),
    CONSTRAINT ck_charge_source CHECK (
        charge_type = 'ADJUSTMENT' OR source_id IS NOT NULL
    ),
    CONSTRAINT ck_charge_reversal_reason CHECK (
        reversal_of_charge_id IS NULL OR reason IS NOT NULL
    )
);

CREATE TABLE payment (
    id                      UUID PRIMARY KEY,
    folio_id                UUID          NOT NULL REFERENCES folio(id),
    method                  VARCHAR(20)   NOT NULL
                            CHECK (method IN ('CASH','PIX','CREDIT_CARD',
                                              'DEBIT_CARD','ROOM_ACCOUNT')),
    amount                  NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    status                  VARCHAR(20)   NOT NULL DEFAULT 'CONFIRMED'
                            CHECK (status IN ('PENDING','CONFIRMED','FAILED','REFUNDED')),
    idempotency_key         VARCHAR(100)  NOT NULL,
    external_reference      VARCHAR(255),
    payment_intent_id       UUID,
    cash_drawer_session_id  UUID,          -- FK na V8 (task 2.4)
    paid_at                 TIMESTAMPTZ   NOT NULL DEFAULT now(),
    received_by             UUID,
    refunded_at             TIMESTAMPTZ,
    refunded_by             UUID,
    refund_reason           TEXT,
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by              UUID,
    updated_at              TIMESTAMPTZ,
    updated_by              UUID,
    CONSTRAINT uk_payment_idempotency UNIQUE (idempotency_key),
    CONSTRAINT uk_payment_intent      UNIQUE (payment_intent_id),
    CONSTRAINT ck_payment_operator CHECK (
        received_by IS NOT NULL OR payment_intent_id IS NOT NULL
    ),
    CONSTRAINT ck_payment_refunded CHECK (
        status <> 'REFUNDED'
        OR (refunded_at IS NOT NULL AND refunded_by IS NOT NULL AND refund_reason IS NOT NULL)
    )
);

-- payment_intent: exatamente como em docs/schema-banco-de-dados.md §7, com a
-- FK de payment.payment_intent_id. Nasce agora, sem código até a v1.1.

CREATE INDEX idx_charge_folio   ON charge (folio_id);
CREATE INDEX idx_payment_folio  ON payment (folio_id);
CREATE UNIQUE INDEX idx_folio_open_ref ON folio (property_id, reference_code)
    WHERE status = 'OPEN' AND folio_type = 'STAY';
-- + os índices de payment_intent do schema
```

Mapeamento:
- `Folio`, `Charge` e `Payment` estendem `AuditedEntity`
- `Charge` usa `SINGLE_TABLE` com discriminador `charge_type`
- `charges` e `payments` são `@OneToMany` EAGER com `@Fetch(SUBSELECT)` (#17 da 1.2)
- Não mapeados: `reference_date`, `external_reference`, `payment_intent_id`, `cash_drawer_session_id`

---

## 3. Invariantes

### `Folio`

1. Pertence a uma propriedade. Nasce `OPEN`, com `openedAt`
2. `STAY` tem dono `RESERVATION` e `FolioReference` obrigatório. `TAB` tem dono
   `TAB` e nenhuma referência. Dono do tipo errado é `IllegalArgumentException`
   (erro de programação entre módulos, não código de negócio)
3. Um folio por dono. Um segundo para o mesmo dono dá
   `FOLIO_ALREADY_OPENED_FOR_OWNER`, checado no caso de uso e garantido por `uk_folio_owner`
4. `FolioReference`: `code` aparado, de 1 a 20 caracteres; `label` aparado, de 1 a 150
5. **`balance()` = soma de todos os `Charge` (com sinal) − soma dos `Payment`
   `CONFIRMED`.** Calculado, nunca armazenado. Pagamento `REFUNDED` não abate
6. Folio `CLOSED` recusa toda escrita: lançar, estornar, ajustar, receber,
   estornar pagamento, trocar referência e fechar de novo (`FOLIO_CLOSED`)
7. `close()` só com `balance()` exatamente zero (`FOLIO_BALANCE_NOT_ZERO`), #4.
   Grava `closedAt` e `closedBy`. Folio sem lançamento, saldo zero, fecha.
   O saldo diferente de zero se resolve com um `AdjustmentCharge` do `ADMIN` antes
8. `changeReference` só em folio `STAY`; em `TAB` é `IllegalArgumentException`.
   O novo código não pode estar em uso por outro `STAY` aberto
   (`FOLIO_REFERENCE_ALREADY_IN_USE`, #17)
9. `charges()` e `payments()` imutáveis para fora, em ordem de criação (UUIDv7)
10. Fechar nunca é automático (#8)

### `Charge`

11. Lançamento pela fachada (`post`): valor **maior que zero**
    (`INVALID_CHARGE_AMOUNT`); descrição aparada, 1 a 255 (`INVALID_CHARGE_DESCRIPTION`)
12. O `ChargeRequest` carrega a origem (`ChargeSource`, #9): `ROOM_NIGHT` gera
    `RoomNightCharge` e só é aceito em folio `STAY` (`CHARGE_TYPE_NOT_ACCEPTED`);
    `TAB` gera `TabCharge`, aceito nos dois tipos. O id da origem vai em `source_id`
13. `AdjustmentCharge`: valor **diferente de zero** (acréscimo ou desconto);
    descrição como na 11; motivo aparado, 1 a 500 (`INVALID_CHARGE_REASON`);
    `authorizedBy` = usuário autenticado (o perfil `ADMIN` é garantido na rota)
14. Lançamento é append-only: nunca alterado nem removido

### Estorno de lançamento (`reverse`), #7

15. O lançamento precisa pertencer ao folio (`CHARGE_NOT_FOUND`)
16. Só `RoomNightCharge` e `TabCharge` que **não** são estorno. Ajuste e linha de
    estorno dão `CHARGE_NOT_REVERSIBLE`
17. Uma vez só (`CHARGE_ALREADY_REVERSED`), garantido também por `uk_charge_reversal`
18. O estorno é um lançamento novo, do mesmo tipo e origem, valor = **−original**,
    mesma descrição, `reason` obrigatório (regra da 13), `reversalOf` = original.
    O original fica intacto; o saldo cai exatamente o valor do original

### `Payment`

19. Valor maior que zero (`INVALID_PAYMENT_AMOUNT`), chega como string decimal
20. `ROOM_ACCOUNT` recusado no registro manual (`PAYMENT_METHOD_NOT_ACCEPTED`, #6).
    `CASH`, `PIX`, `CREDIT_CARD` e `DEBIT_CARD` valem nos dois tipos
21. Acima do saldo: folio `TAB` recusa (`PAYMENT_EXCEEDS_BALANCE`); folio `STAY`
    aceita e fica com saldo negativo, crédito do hóspede (#1)
22. Pagamento manual nasce `CONFIRMED`, com `receivedBy` e `paidAt`
23. **Idempotência** (#5). Header `Idempotency-Key`, aparado, 1 a 100
    (`INVALID_IDEMPOTENCY_KEY`)
    - Chave já usada **no mesmo folio, mesmo método e mesmo valor**: devolve o
      pagamento existente, sem criar outro. Checado **antes** de qualquer outra
      regra, inclusive `FOLIO_CLOSED`
    - Chave já usada com qualquer diferença: `IDEMPOTENCY_KEY_REUSED`
24. Estorno de pagamento (`refund(reason)`, #2): só pagamento `CONFIRMED`
    (`PAYMENT_ALREADY_REFUNDED`), motivo 1 a 500 (`INVALID_PAYMENT_REFUND_REASON`),
    folio aberto. Grava `refundedAt`, `refundedBy`, `refundReason`. O saldo sobe
    exatamente o valor do pagamento

### Concorrência

25. Toda escrita no folio carrega-o com `SELECT ... FOR UPDATE`
    (`PESSIMISTIC_WRITE`) antes de decidir. Sem isso, um lançamento junto com o
    fechamento produz folio fechado com saldo ≠ 0. Leitura não trava

---

## 4. Assinaturas públicas

### Acréscimos ao `billing/api` (#9, #10, #11)

```java
// novo
public enum ChargeSourceType { ROOM_NIGHT, TAB }
public record ChargeSource(ChargeSourceType type, UUID id) { /* não nulos */ }

// ChargeRequest ganha a origem
public record ChargeRequest(Money amount, String description, ChargeSource source)

// FolioView.reference passa a Optional<FolioReference> (folio TAB não tem)

// FolioFacade ganha
void close(FolioId folioId);
```

Atualize o javadoc do que mudar citando a decisão desta task. Nenhum outro módulo
consome o `billing/api` hoje, então o acréscimo não quebra ninguém.

### Domínio (`billing.domain`)

Tipos internos: `PaymentId`, `PaymentMethod` (`acceptsManualEntry()`),
`PaymentStatus` (`countsTowardsBalance()`), `ChargeType`.

```java
// Folio
static Folio openForStay(UUID propertyId, FolioOwner reservation, FolioReference reference, Instant openedAt);
static Folio openForTab(UUID propertyId, FolioOwner tab, Instant openedAt);
Charge post(ChargeRequest request);
AdjustmentCharge postAdjustment(Money amount, String description, String reason, UUID authorizedBy);
Charge reverse(ChargeId chargeId, String reason);
Payment receive(PaymentMethod method, Money amount, String idempotencyKey, UUID receivedBy, Instant paidAt);
void refund(PaymentId paymentId, String reason, UUID refundedBy, Instant refundedAt);
void changeReference(FolioReference newReference);
void close(UUID closedBy, Instant closedAt);
Money balance();  Money totalCharges();  Money totalPayments();
FolioId id(); UUID propertyId(); FolioType type(); FolioStatus status(); FolioOwner owner();
Optional<FolioReference> reference(); Instant openedAt(); Optional<Instant> closedAt();
List<Charge> charges(); List<Payment> payments();
Optional<Payment> paymentWithKey(String idempotencyKey);   // replay

// Charge (abstrata) → RoomNightCharge, TabCharge, AdjustmentCharge
ChargeId id(); Money amount(); String description(); Optional<ChargeId> reversalOf();
Optional<String> reason(); Optional<UUID> authorizedBy(); boolean isReversal(); boolean isReversible();

// Payment
PaymentId id(); PaymentMethod method(); Money amount(); PaymentStatus status();
String idempotencyKey(); Instant paidAt(); boolean isConfirmed();
boolean matches(PaymentMethod method, Money amount);
```

A unicidade de `FolioReference` entre folios (#17) e a de chave de idempotência
entre folios são regras de conjunto: checadas no caso de uso e garantidas pelos
índices. `FolioRepository` no domínio com `findById`, `findByIdForUpdate`,
`findOpenStayByReferenceCode`, `existsByOwner`, `findPaymentByIdempotencyKey`,
`save`. `FolioService` implementa o `FolioFacade` e os casos de uso REST; só
orquestra, com `AuditorAware` e `Clock` injetados.

---

## 5. Códigos de erro

| Código | HTTP | Quando |
|---|---|---|
| `FOLIO_NOT_FOUND` | 404 | O folio não existe |
| `CHARGE_NOT_FOUND` | 404 | O lançamento não existe nesse folio |
| `PAYMENT_NOT_FOUND` | 404 | O pagamento não existe nesse folio |
| `FOLIO_CLOSED` | 409 | Qualquer escrita em folio fechado |
| `FOLIO_BALANCE_NOT_ZERO` | 409 | Fechar com saldo diferente de zero |
| `FOLIO_ALREADY_OPENED_FOR_OWNER` | 409 | Segundo folio para o mesmo dono |
| `FOLIO_REFERENCE_ALREADY_IN_USE` | 409 | Outro folio `STAY` aberto já usa o código |
| `CHARGE_ALREADY_REVERSED` | 409 | Estornar lançamento já estornado |
| `PAYMENT_ALREADY_REFUNDED` | 409 | Estornar pagamento já estornado |
| `IDEMPOTENCY_KEY_REUSED` | 409 | Chave já usada com folio, método ou valor diferente |
| `CHARGE_NOT_REVERSIBLE` | 422 | Estornar ajuste ou linha de estorno |
| `CHARGE_TYPE_NOT_ACCEPTED` | 422 | Diária em folio `TAB` |
| `INVALID_CHARGE_AMOUNT` | 422 | Lançamento ≤ 0, ou ajuste = 0 |
| `INVALID_CHARGE_DESCRIPTION` | 422 | Descrição em branco ou acima de 255 |
| `INVALID_CHARGE_REASON` | 422 | Motivo de ajuste ou estorno em branco ou acima de 500 |
| `INVALID_FOLIO_REFERENCE` | 422 | `code` fora de 1–20 ou `label` fora de 1–150 |
| `INVALID_PAYMENT_AMOUNT` | 422 | Pagamento ≤ 0 |
| `PAYMENT_METHOD_NOT_ACCEPTED` | 422 | `ROOM_ACCOUNT` no registro manual |
| `PAYMENT_EXCEEDS_BALANCE` | 422 | Pagamento acima do saldo em folio `TAB` |
| `INVALID_PAYMENT_REFUND_REASON` | 422 | Motivo do estorno em branco ou acima de 500 |
| `INVALID_IDEMPOTENCY_KEY` | 422 | Header ausente, em branco ou acima de 100 |

Valor mal formado reusa os códigos de `Money` do shared-kernel. Método fora do
enum: 400 do handler global.

---

## 6. API

```
GET  /api/billing/folios/{folioId}                               ADMIN, FRONT_DESK
GET  /api/billing/folios?referenceCode=102                       ADMIN, FRONT_DESK   só STAY aberto; 404 se não houver
POST /api/billing/folios/{folioId}/payments                      ADMIN, FRONT_DESK   Idempotency-Key  {method, amount}  201
POST /api/billing/folios/{folioId}/payments/{paymentId}/refund   ADMIN               {reason}
POST /api/billing/folios/{folioId}/adjustments                   ADMIN               {amount, description, reason}  201
POST /api/billing/folios/{folioId}/charges/{chargeId}/reversal   ADMIN, FRONT_DESK   {reason}  201
POST /api/billing/folios/{folioId}/close                         ADMIN, FRONT_DESK
```

`WAITER` e `KITCHEN`: 403 em tudo nesta task (#3).

- **Resposta do folio** (todas menos o pagamento): `{id, type, status,
  reference: {code, label} | null, openedAt, closedAt, charges: [{id, type,
  amount, description, reason, reversalOf, reversedBy, createdAt, createdBy}],
  payments: [{id, method, amount, status, paidAt, receivedBy, refundedAt,
  refundReason}], totalCharges, totalPayments, balance}`. `reversedBy` é
  calculado na leitura
- **Resposta do pagamento:** `{id, method, amount, status, paidAt, balance}`
  (saldo depois do pagamento). O replay idempotente responde **201 com o mesmo
  corpo**, saldo recalculado
- `Idempotency-Key` lido como header **opcional** no binding; a ausência é
  recusada pelo domínio (`INVALID_IDEMPOTENCY_KEY`), porque header obrigatório
  ausente hoje cai no catch-all com 500
- Valores em string decimal. Corpo sem bean validation para o que o domínio
  recusa com código (#21 da 1.2)

**Rota só do perfil `dev`** (#12), para o `.http`:
`POST /api/billing/dev/folios` `{type, referenceCode?, referenceLabel?}` e
`POST /api/billing/dev/folios/{folioId}/charges` `{sourceType, amount, description}`.
Atrás de `@Profile("dev")` + `castel.dev.seed-enabled`, como o `DevUserSeeder`,
`ADMIN`, e só chamam o `FolioFacade` (o dono é um UUID novo).

---

## 7. Testes

Dinheiro: denso. Alvo até **1,3:1**, o excedente em saldo, estorno,
fechamento e concorrência. Enxuto em validação de texto (uma borda por limite).

- **Unidade (TEST):**
  - Saldo: só lançamentos; pagamento parcial; múltiplas formas; estorno; ajuste
    positivo e negativo; pagamento estornado deixa de abater; saldo negativo em
    `STAY`; centavos (0,01) sem arredondamento
  - Transições: cada escrita em folio `CLOSED` recusada; fechar com saldo
    +0,01, −0,01 e 0; fechar folio vazio
  - Estorno: do original; de estorno; de ajuste; duas vezes; de lançamento
    alheio; o saldo depois
  - Pagamento: ≤ 0; `ROOM_ACCOUNT`; acima do saldo em `TAB` (exato aceito,
    +0,01 recusado); acima em `STAY` aceito; replay e chave reusada
  - Estorno de pagamento: duas vezes; em folio fechado
  - Abertura: dono do tipo errado; `STAY` sem referência; limites da referência;
    diária em folio `TAB`
- **Integração (DEV), `app/src/test/.../billing/`:**
  1. Fatia REST + fachada: abre, lança, paga parcial em duas formas, estorna
     lançamento, ajusta como `ADMIN`, fecha. 403 do `WAITER` e do `FRONT_DESK` no ajuste
  2. Idempotência: mesmo pedido duas vezes dá um pagamento; mesma chave com
     valor diferente dá 409; retry depois do fechamento devolve o original
  3. **Concorrência:** dois pagamentos simultâneos com a mesma chave geram um registro só
  4. **Concorrência:** fechamento simultâneo a um lançamento nunca deixa folio
     `CLOSED` com saldo ≠ 0
  5. `findOpenStayFolioByCode` não acha folio fechado; `changeReference` libera o código antigo

---

## 8. `.http`

`http/40-billing-folios.http`, no padrão do `32`.

- **Caminho feliz:** abre um folio `TAB` e um `STAY` pela rota de dev; lança
  "120.00"; paga "50.00" em `PIX`; repete o mesmo pagamento (mesmo id); paga
  "70.00" em `CASH`; fecha. No `STAY`: lança, estorna com motivo, `ADMIN` ajusta,
  estorna um pagamento, fecha com saldo zero; busca pelo `referenceCode`
- **Negativos:** um por código da seção 5 alcançável por REST, mais 401 sem
  token, 403 do `WAITER` (leitura) e do `FRONT_DESK` (ajuste). Chave de
  idempotência e código de referência com sufixo aleatório por execução

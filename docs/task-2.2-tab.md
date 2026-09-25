# Task 2.2 — Comanda: abertura, lançamento e cancelamento

**Objetivo:** abrir comanda em mesa ou cartão do self-service, lançar itens (por
unidade, com variação, adicionais e observação, ou por peso) com preço congelado
no lançamento, cancelar item com autor, horário e motivo, e cancelar a comanda
aberta por engano.

Decisões: `docs/decisions/task-2.2.md`. Herdadas: 1.2 #1–#3, #8, #10–#12, #26 e
1.5 #1, #2, #9. Gabarito de estilo: o módulo `restaurant` (`MenuItem` guarda o id
de outro agregado, nunca a entidade; `DiningTableService` e `DiningTableController`
para caso de uso e rota).

Risco alto: dinheiro (preço congelado, peso × preço/kg, adicionais), transição
de status e concorrência real. Teste denso nesses pontos.

---

## 1. Escopo

**Dentro**
- Agregado `Tab` com `TabItem` e `TabItemModifier`
- `TabStatus`, `TabOrigin`, `TabItemStatus` com comportamento
- Abrir comanda em mesa (`TABLE_SERVICE`) ou cartão (`SELF_SERVICE`); no máximo
  uma comanda ativa por mesa e por cartão (#1)
- Lançar item por unidade (variação, adicionais com quantidade, observação) ou
  por peso (gramas × preço/kg)
- Cancelar item com autor, horário e motivo (#6)
- Cancelar comanda sem item ativo, com autor, horário e motivo (#10)
- `subtotal()` calculado
- Ler uma comanda; listar as ativas, com filtro por mesa e por cartão
- `DiningTableService.deactivate` recusa mesa com comanda ativa (#8)
- `MenuItem.isWithinAvailabilityWindowAt(Instant, ZoneId)` (novo, para separar
  "esgotado" de "fora do horário")
- Migration `V7__tab.sql` e seção 11 de `docs/schema-banco-de-dados.md` atualizada
- `http/33-restaurant-tabs.http`

**Fora**
- Fechamento, `CLOSING`/`CLOSED`, taxa de serviço aplicada, `total()`, destino,
  `folio_id`, divisão (`split_group`): task 3.2, migration `V11__tab_closing.sql`
- Transições do KDS e eventos `TabItemOrdered`/`TabItemCancelled`: task 3.5
- Transferência de item e junção de comandas: task 3.6 (as colunas entram já)
- QR code do `publicToken`: v1.1 (gerado e gravado, não sai na API)
- `http/README.md`, `CLAUDE.md`, `docs/MIGRATIONS.md`: quem edita é o orquestrador

---

## 2. Modelo de dados

`V7__tab.sql`, a partir de `docs/schema-banco-de-dados.md` seção 11. Atualize a
seção 11 do documento antes de escrever a migration. Diferenças:

- **Saem da V7, vão para a V11 da 3.2:** `folio_id` (o `folio` é criado pela 1.3
  em paralelo), `ck_tab_closed`, `destination`, `closing_started_at`,
  `closed_at`, `closed_by`, `service_charge_applied`, `service_charge_rate`,
  `guest_count`, `tab_item.split_group`
- **Entram já, sem mapeamento:** instantes do KDS (3.5), `transferred_*` e
  `merged_*` (3.6), com seus `CHECK`. Evita `ALTER` concorrente das tasks da Onda 3
- **Novo:** `tab_item.variant_name` (nome congelado da variação); as quatro
  colunas de auditoria em `tab_item`; `tab_item_modifier` com PK composta, sem
  `id`, no padrão da `menu_item_modifier`; `tab.cancelled_*` (#10)
- **`ck_tab_item_pricing` mais restrito:** item por peso tem quantidade 1 e não
  tem variação; item por unidade não tem `price_per_kilo`
- **Sem `ON DELETE CASCADE` em `tab_item.tab_id`:** comanda nunca é apagada

```sql
-- Task 2.2 - tabs: opening, items and cancellation.
-- Closing, service charge, destination, folio_id and split_group arrive with
-- task 3.2 (V11). KDS timestamps (3.5) and transfer and merge columns (3.6) are
-- created here, unmapped until their task.

CREATE TABLE tab (
    id                  UUID PRIMARY KEY,
    property_id         UUID        NOT NULL REFERENCES property(id),
    origin              VARCHAR(20) NOT NULL
                        CHECK (origin IN ('TABLE_SERVICE','SELF_SERVICE')),
    dining_table_id     UUID        REFERENCES dining_table(id),
    card_number         INTEGER     CHECK (card_number BETWEEN 1 AND 999),
    status              VARCHAR(20) NOT NULL DEFAULT 'OPEN'
                        CHECK (status IN ('OPEN','CLOSING','CLOSED','CANCELLED','MERGED')),
    public_token        UUID        NOT NULL,
    opened_by           UUID        NOT NULL,
    opened_at           TIMESTAMPTZ NOT NULL,
    merged_into_tab_id  UUID        REFERENCES tab(id),
    merged_at           TIMESTAMPTZ,
    merged_by           UUID,
    cancelled_at        TIMESTAMPTZ,
    cancelled_by        UUID,
    cancellation_reason TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by          UUID,
    updated_at          TIMESTAMPTZ,
    updated_by          UUID,
    CONSTRAINT uk_tab_public_token UNIQUE (public_token),
    CONSTRAINT ck_tab_origin CHECK (
        (origin = 'TABLE_SERVICE' AND dining_table_id IS NOT NULL AND card_number IS NULL)
     OR (origin = 'SELF_SERVICE'  AND card_number     IS NOT NULL AND dining_table_id IS NULL)
    ),
    CONSTRAINT ck_tab_merged CHECK (
        status <> 'MERGED'
        OR (merged_into_tab_id IS NOT NULL AND merged_at IS NOT NULL AND merged_by IS NOT NULL)
    ),
    CONSTRAINT ck_tab_no_self_merge CHECK (merged_into_tab_id <> id),
    CONSTRAINT ck_tab_cancelled CHECK (
        status <> 'CANCELLED'
        OR (cancelled_at IS NOT NULL AND cancelled_by IS NOT NULL AND cancellation_reason IS NOT NULL)
    )
);

CREATE TABLE tab_item (
    id                      UUID PRIMARY KEY,
    tab_id                  UUID          NOT NULL REFERENCES tab(id),
    menu_item_id            UUID          NOT NULL REFERENCES menu_item(id),
    menu_item_variant_id    UUID          REFERENCES menu_item_variant(id),
    item_name               VARCHAR(200)  NOT NULL,
    variant_name            VARCHAR(50),
    quantity                SMALLINT      NOT NULL DEFAULT 1 CHECK (quantity BETWEEN 1 AND 999),
    weight_grams            INTEGER       CHECK (weight_grams BETWEEN 1 AND 50000),
    unit_price              NUMERIC(12,2) CHECK (unit_price > 0),
    price_per_kilo          NUMERIC(12,2) CHECK (price_per_kilo > 0),
    line_total              NUMERIC(12,2) NOT NULL CHECK (line_total >= 0),
    service_chargeable      BOOLEAN       NOT NULL,
    special_instructions    TEXT,
    prep_station            VARCHAR(20)   NOT NULL
                            CHECK (prep_station IN ('KITCHEN','PIZZA','BAR')),
    status                  VARCHAR(20)   NOT NULL DEFAULT 'PENDING'
                            CHECK (status IN ('PENDING','IN_PREPARATION','READY',
                                              'DELIVERED','CANCELLED')),
    ordered_by              UUID          NOT NULL,
    ordered_at              TIMESTAMPTZ   NOT NULL,
    preparation_started_at  TIMESTAMPTZ,
    ready_at                TIMESTAMPTZ,
    delivered_at            TIMESTAMPTZ,
    cancelled_at            TIMESTAMPTZ,
    cancelled_by            UUID,
    cancellation_reason     TEXT,
    transferred_from_tab_id UUID          REFERENCES tab(id),
    transferred_at          TIMESTAMPTZ,
    transferred_by          UUID,
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by              UUID,
    updated_at              TIMESTAMPTZ,
    updated_by              UUID,
    CONSTRAINT ck_tab_item_pricing CHECK (
        (weight_grams IS NOT NULL AND price_per_kilo IS NOT NULL AND unit_price IS NULL
            AND quantity = 1 AND menu_item_variant_id IS NULL)
     OR (weight_grams IS NULL AND price_per_kilo IS NULL AND unit_price IS NOT NULL)
    ),
    CONSTRAINT ck_tab_item_variant_name CHECK (
        (menu_item_variant_id IS NULL) = (variant_name IS NULL)
    ),
    CONSTRAINT ck_tab_item_cancelled CHECK (
        status <> 'CANCELLED'
        OR (cancelled_at IS NOT NULL AND cancelled_by IS NOT NULL
            AND cancellation_reason IS NOT NULL)
    ),
    CONSTRAINT ck_tab_item_transferred CHECK (
        transferred_from_tab_id IS NULL
        OR (transferred_at IS NOT NULL AND transferred_by IS NOT NULL)
    )
);

CREATE TABLE tab_item_modifier (
    tab_item_id     UUID          NOT NULL REFERENCES tab_item(id) ON DELETE CASCADE,
    modifier_id     UUID          NOT NULL REFERENCES modifier(id),
    modifier_name   VARCHAR(100)  NOT NULL,
    price           NUMERIC(12,2) NOT NULL CHECK (price >= 0),
    quantity        SMALLINT      NOT NULL CHECK (quantity BETWEEN 1 AND 99),
    PRIMARY KEY (tab_item_id, modifier_id)
);

CREATE UNIQUE INDEX idx_tab_open_by_table
    ON tab (dining_table_id)
    WHERE status IN ('OPEN','CLOSING') AND dining_table_id IS NOT NULL;

CREATE UNIQUE INDEX idx_tab_open_by_card
    ON tab (property_id, card_number)
    WHERE status IN ('OPEN','CLOSING') AND card_number IS NOT NULL;

CREATE INDEX idx_tab_item_by_tab ON tab_item (tab_id);
CREATE INDEX idx_tab_merged_into ON tab (merged_into_tab_id)
    WHERE merged_into_tab_id IS NOT NULL;
CREATE INDEX idx_kds_queue
    ON tab_item (prep_station, status, ordered_at)
    WHERE status IN ('PENDING','IN_PREPARATION');
```

---

## 3. Invariantes

### Abertura
1. Pertence a uma propriedade. Nasce `OPEN`, com `openedBy`, `openedAt` e
   `publicToken` (UUID v4 aleatório, diferente do id, único)
2. `openForTable`: a mesa precisa estar ativa (`INACTIVE_DINING_TABLE`). Guarda
   só o `DiningTableId`
3. `openForSelfService`: cartão de 1 a 999 (`INVALID_CARD_NUMBER`, #2). Sem cadastro de cartão
4. No máximo uma comanda `OPEN` ou `CLOSING` por mesa e por cartão (#1). Garantido
   pelos índices únicos parciais; a infra traduz a violação em
   `TAB_ALREADY_OPEN_FOR_DINING_TABLE`/`TAB_ALREADY_OPEN_FOR_CARD` (409), inclusive
   quando duas aberturas chegam juntas. Sem consulta prévia
5. A abertura do self-service é explícita (#4): o front encadeia "busca pelo
   cartão → abre se não houver → lança"

### Lançamento (`addItem`)
6. Só com `TabStatus.acceptsItems()`, verdadeiro apenas em `OPEN` (`TAB_NOT_OPEN`)
7. Item inativo, esgotado, ou com todas as variações ativas esgotadas:
   `MENU_ITEM_UNAVAILABLE`. Fora da janela de horário no fuso da propriedade:
   `MENU_ITEM_OUTSIDE_AVAILABILITY_WINDOW`. Os dois são **recusados** (#5)
8. **Por peso** (1.2 #3): exige `weightGrams` de 1 a 50.000 (`Weight`); recusa
   variação, adicional e quantidade diferente de 1; preço/kg vem do item
9. **Por unidade:** recusa `weightGrams`; `quantity` opcional, padrão 1, de 1 a
   999 (`Quantity`); se `requiresVariant()`, variação obrigatória; variação
   informada precisa ser do item (404) e estar ativa e disponível; com variação
   o preço é o dela (1.2 #1), sem variação é o `price()` do item
10. **Adicionais** (só por unidade): cada um oferecido no item e ativo;
    quantidade de 1 até o `maxQuantity` do item, contada por unidade; sem repetir
    o mesmo adicional; preço zero válido
11. **Valor da linha:**
    - por unidade: `(preço unitário + Σ preço do adicional × quantidade do adicional) × quantidade`
    - por peso: `Weight.priceAt(pricePerKilo)`, centavo arredondado meio para cima (#7)
    - calculado uma vez no lançamento e gravado em `line_total`; nunca muda
12. **Preço congelado:** grava nome do item, nome da variação, preço unitário ou
    preço/kg, setor de preparo e, em cada adicional, nome e preço. Mudar o
    cardápio depois não altera a comanda
13. `serviceChargeable = origin.chargesServiceByDefault() && menuItem.serviceChargeEligible()`.
    `TABLE_SERVICE` nasce com taxa, `SELF_SERVICE` sem. O adicional segue o item (1.2 #8)
14. Observação: aparada, em branco vira `null`, no máximo 200 caracteres
15. Status inicial: `PENDING`. **Item por peso nasce `DELIVERED`**, com
    `delivered_at = ordered_at`: o cliente já se serviu e o prato não vai para a
    tela da cozinha (#9). Bebida lançada no cartão nasce `PENDING` e vai para o bar

### Cancelamento de item
16. Só em comanda `OPEN` (`TAB_NOT_OPEN`)
17. `TabItemStatus.acceptsCancellation()`: verdadeiro em qualquer status menos
    `CANCELLED`, **inclusive `DELIVERED`** (#6). Já cancelado:
    `TAB_ITEM_ALREADY_CANCELLED` (409), sem sobrescrever autor nem motivo
18. Grava `cancelledBy`, `cancelledAt` e motivo **obrigatório** em texto livre,
    aparado, 1 a 500 (`INVALID_CANCELLATION_REASON`). O item fica na comanda para sempre

### Cancelamento da comanda (#10)
19. Só em comanda `OPEN` sem nenhum item ativo (`TAB_HAS_ACTIVE_ITEMS`, 409)
20. Vai para `CANCELLED` com `cancelledBy`, `cancelledAt` e motivo (mesma regra da 18).
    Libera a mesa ou o cartão (o índice só conta `OPEN`/`CLOSING`)

### Valores derivados
21. `subtotal()` = soma dos `lineTotal` dos itens não cancelados. Calculado.
    `serviceCharge()` e `total()` nascem na 3.2

### Mesa
22. Desativar mesa com comanda `OPEN` ou `CLOSING`: `DINING_TABLE_HAS_OPEN_TAB`
    (409), #8. Checado no caso de uso (regra sobre o conjunto de comandas)

---

## 4. Assinaturas públicas

`TabId` e `TabItemId` em `restaurant.domain`, no formato de `DiningTableId`. O
`billing` recebe a comanda como `FolioOwner.tab(UUID)` (0.6 #1).

```java
// Tab
static Tab openForTable(UUID propertyId, DiningTable diningTable, UUID openedBy, Instant openedAt);
static Tab openForSelfService(UUID propertyId, int cardNumber, UUID openedBy, Instant openedAt);

TabItem addItem(MenuItem menuItem, TabItemOrder order, UUID orderedBy,
                Instant orderedAt, ZoneId propertyZone);
void cancelItem(TabItemId itemId, String reason, UUID cancelledBy, Instant cancelledAt);
void cancel(String reason, UUID cancelledBy, Instant cancelledAt);

TabId id(); UUID propertyId(); TabOrigin origin(); TabStatus status();
Optional<DiningTableId> diningTableId(); Optional<Integer> cardNumber();
UUID publicToken(); UUID openedBy(); Instant openedAt();
List<TabItem> items();          // unmodifiable, por orderedAt e depois id
TabItem item(TabItemId id);     // TAB_ITEM_NOT_FOUND
Money subtotal();

// O pedido (nomes aprovados no glossário, #11)
record TabItemOrder(MenuItemVariantId variantId,     // nullable
                    Integer quantity,                // nullable, padrão 1
                    Integer weightGrams,             // nullable
                    List<ModifierChoice> modifiers,  // pode ser vazia
                    String specialInstructions) {}
record ModifierChoice(Modifier modifier, int quantity) {}

// TabItem
TabItemId id(); MenuItemId menuItemId(); Optional<MenuItemVariantId> variantId();
String itemName(); Optional<String> variantName(); int quantity();
Optional<Weight> weight(); Optional<Money> unitPrice(); Optional<Money> pricePerKilo();
List<TabItemModifier> modifiers(); Money lineTotal(); boolean serviceChargeable();
Optional<String> specialInstructions(); PrepStation prepStation(); TabItemStatus status();
UUID orderedBy(); Instant orderedAt();
Optional<Instant> cancelledAt(); Optional<UUID> cancelledBy(); Optional<String> cancellationReason();
boolean isActive();

// TabItemModifier (@Embeddable)
ModifierId modifierId(); String modifierName(); Money price(); int quantity();

// Enums
TabStatus      boolean acceptsItems(); boolean acceptsItemCancellation(); boolean holdsItsPlace(); // OPEN, CLOSING
TabOrigin      boolean chargesServiceByDefault();
TabItemStatus  boolean isActive(); boolean acceptsCancellation();

// MenuItem, novo
boolean isWithinAvailabilityWindowAt(Instant moment, ZoneId propertyZone);
```

`TabRepository` no domínio: `findById`, `findByIdForItemEntry` (`FOR KEY SHARE`,
seção 7), `add` (`saveAndFlush` traduzindo `idx_tab_open_by_*` em 409),
`findActive(propertyId, diningTableIdOrNull, cardNumberOrNull)`,
`existsActiveOnDiningTable`. `TabService` com `open`, `addItem`, `cancelItem`,
`cancel`, `find`, `listActive`: autoria por `AuditorAware`, horário por `Clock`,
fuso por `CurrentProperty.timeZone()`; carrega `MenuItem` e `Modifier`s pelos
ids (404 se não existirem) e chama o agregado, sem `if` de regra.

---

## 5. Códigos de erro

| Código | HTTP | Quando |
|---|---|---|
| `TAB_NOT_FOUND` | 404 | A comanda não existe |
| `TAB_ITEM_NOT_FOUND` | 404 | O item não existe nesta comanda |
| `TAB_NOT_OPEN` | 409 | Lançar, cancelar item ou cancelar comanda que não está `OPEN` |
| `TAB_ALREADY_OPEN_FOR_DINING_TABLE` | 409 | A mesa já tem comanda ativa, inclusive em aberturas simultâneas |
| `TAB_ALREADY_OPEN_FOR_CARD` | 409 | O cartão já tem comanda ativa |
| `TAB_ITEM_ALREADY_CANCELLED` | 409 | Cancelar item já cancelado |
| `TAB_HAS_ACTIVE_ITEMS` | 409 | Cancelar comanda com item ativo |
| `DINING_TABLE_HAS_OPEN_TAB` | 409 | Desativar mesa com comanda ativa |
| `INVALID_TAB_OPENING` | 422 | `origin` sem o campo que exige, ou com o campo da outra origem |
| `INVALID_CARD_NUMBER` | 422 | Cartão fora de 1 a 999 |
| `INACTIVE_DINING_TABLE` | 422 | Abrir comanda em mesa desativada |
| `MENU_ITEM_UNAVAILABLE` | 422 | Item inativo, esgotado ou com todas as variações esgotadas |
| `MENU_ITEM_OUTSIDE_AVAILABILITY_WINDOW` | 422 | Fora da janela de horário |
| `MENU_ITEM_VARIANT_REQUIRED` | 422 | Item com variações ativas, pedido sem variação |
| `MENU_ITEM_VARIANT_UNAVAILABLE` | 422 | Variação inativa ou esgotada |
| `SOLD_BY_WEIGHT_REQUIRES_WEIGHT` | 422 | Item por peso sem `weightGrams` |
| `SOLD_BY_WEIGHT_REJECTS_QUANTITY` | 422 | Item por peso com `quantity` diferente de 1 |
| `SOLD_BY_UNIT_REJECTS_WEIGHT` | 422 | Item por unidade com `weightGrams` |
| `MODIFIER_NOT_OFFERED` | 422 | Adicional que o item não oferece |
| `INVALID_TAB_ITEM_MODIFIER_QUANTITY` | 422 | Quantidade do adicional fora de 1 a `maxQuantity` |
| `DUPLICATE_TAB_ITEM_MODIFIER` | 422 | O mesmo adicional duas vezes no pedido |
| `INVALID_SPECIAL_INSTRUCTIONS` | 422 | Observação acima de 200 caracteres |
| `INVALID_CANCELLATION_REASON` | 422 | Motivo ausente, em branco ou acima de 500 |

Reaproveitados: 404 `DINING_TABLE_NOT_FOUND`, `MENU_ITEM_NOT_FOUND`,
`MENU_ITEM_VARIANT_NOT_FOUND`, `MODIFIER_NOT_FOUND`; 422
`SOLD_BY_WEIGHT_REJECTS_VARIANT`, `SOLD_BY_WEIGHT_REJECTS_MODIFIER`,
`INACTIVE_MODIFIER`, e os códigos de `Quantity` e `Weight` do shared-kernel.

**Ordem no `addItem`:** status da comanda → item indisponível → janela →
peso/unidade (variação, quantidade, peso) → variação → adicionais (repetido →
oferecido → ativo → quantidade) → observação.

---

## 6. API

`ADMIN` e `WAITER` em todas as rotas (#3). `KITCHEN` e `FRONT_DESK`: 403.

```
POST /api/restaurant/tabs                                   {origin, diningTableId?, cardNumber?}  201
GET  /api/restaurant/tabs?diningTableId=&cardNumber=        só OPEN e CLOSING, por openedAt
GET  /api/restaurant/tabs/{tabId}
POST /api/restaurant/tabs/{tabId}/items                     {menuItemId, variantId?, quantity?, weightGrams?,
                                                             modifiers?: [{modifierId, quantity}],
                                                             specialInstructions?}                  201
POST /api/restaurant/tabs/{tabId}/items/{itemId}/cancel     {reason}                               200
POST /api/restaurant/tabs/{tabId}/cancel                    {reason}                               200
```

- Cancelamento é `POST .../cancel`, não o `DELETE` da seção 8 do plano: nada é apagado (#13)
- **`TabResponse`:** `{id, origin, status, diningTableId, diningTableLabel, cardNumber,
  openedAt, openedBy, subtotal, cancelledAt, cancelledBy, cancellationReason, items:[...]}`
- **Item:** `{id, menuItemId, itemName, variantId, variantName, quantity, weightGrams,
  unitPrice, pricePerKilo, modifiers:[{modifierId, name, price, quantity}], lineTotal,
  serviceChargeable, specialInstructions, prepStation, status, orderedAt, orderedBy,
  cancelledAt, cancelledBy, cancellationReason}`
- **Lista:** `{id, origin, status, diningTableId, diningTableLabel, cardNumber, openedAt,
  subtotal, activeItemCount}`
- Lançar devolve 201 com o `TabResponse`; cancelar devolve 200 com ele
- Dinheiro como string decimal; peso em gramas inteiras; autores como UUID
- Sem bean validation para o que o domínio recusa. `origin` ausente ou
  desconhecido e `menuItemId` ausente: 400

---

## 7. Concorrência

- Sem `@Version` na `Tab`: dois garçons lançando são dois `INSERT` em `tab_item`
- `addItem`, `cancelItem` e `cancel` carregam a comanda com `SELECT ... FOR KEY
  SHARE` (consulta nativa): compatível entre lançamentos simultâneos, bloqueado
  pelo `FOR UPDATE` que o fechamento da 3.2 vai usar
- Abertura: a garantia é o índice único parcial; `saveAndFlush` e tradução do
  nome do índice. O caminho normal e o da corrida são o mesmo

---

## 8. Testes

Produção estimada ~1.400 linhas; alvo ~1.400 de teste.

**Unidade (TEST), densa:**
- Dinheiro: variação substitui o preço do item; `(45,00 + 2 × 8,50 + 0,00) × 3 =
  186,00`; adicional de preço zero; 437 g × 59,90 = 26,18; empate meio para cima
  125 g × 0,20 = 0,03; 50.000 g; `subtotal` ignora cancelados; preço congelado
  sobrevive a troca de preço e nome no cardápio
- Taxa: `serviceChargeable` por origem × elegibilidade (3 casos)
- Status: `TabStatus` × lançar/cancelar; `TabItemStatus` × cancelar; item por
  peso nasce `DELIVERED`
- Regras de pedido: um caso por código da seção 5, e a ordem das checagens
- Abertura: bordas do cartão; mesa inativa; `publicToken` diferente do id
- Cancelar comanda: sem itens; com item só cancelado; com item ativo recusado

**Integração (DEV), no `app`:**
- Fatia: abre mesa → lança pizza G com adicional e observação → lança prato por
  peso num cartão → lê → cancela item → lista → cancela comanda vazia
- `KITCHEN` recebe 403; desativar mesa com comanda dá 409
- **Concorrência (Testcontainers):** (a) 2 threads lançam na mesma comanda: dois
  201, dois itens; (b) 5 threads abrem a mesma mesa: um 201 e quatro 409, nunca
  500; (c) idem por cartão

---

## 9. `.http`

`http/33-restaurant-tabs.http`, no padrão do `32`. Preparação: login do admin e
do garçom; cria mesa com sufixo aleatório; cria pizza P/G, borda recheada
(`maxQuantity` 2), "ponto da carne" a zero, buffet a 59,90/kg e um item com janela
num dia que não é hoje. Cartão aleatório na faixa. Caminho feliz: abre mesa,
lança, lê, cancela item, lista por mesa e por cartão, cancela a comanda do
cartão depois de cancelar os itens. Negativos: um por código da seção 5, mais
401 sem token e 403 da cozinha. **No fim, cancele as comandas abertas**, para o
arquivo poder rodar de novo.

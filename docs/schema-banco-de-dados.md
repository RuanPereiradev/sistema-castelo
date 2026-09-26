# Schema do banco de dados

PostgreSQL 16 · Flyway forward-only · 25 tabelas

Este documento é a fonte da verdade do schema. Nenhum agente cria tabela ou
coluna que não esteja aqui. Se precisar de algo novo, o documento é atualizado
primeiro.

---

## 1. Decisões estruturais

### 1.1 A direção das FKs segue a dependência entre módulos

Esta é a decisão que mais afeta o schema, e ela é contraintuitiva.

O módulo `billing` não conhece `hotel` nem `restaurant`. Logo **não existe FK
saindo de `folio` para `reservation` ou `tab`**. É o contrário: `reservation` e
`tab` guardam `folio_id`.

```
reservation.folio_id ──> folio        (hotel depende de billing.api)
tab.folio_id ────────> folio          (restaurant depende de billing.api)
folio ──X──> reservation              (proibido: inverteria a dependência)
```

Três consequências práticas:

1. As migrations de `billing` rodam **antes** das de `hotel` e `restaurant`, sem
   depender de tabelas que ainda não existem.
2. `charge.source_id` aponta para `room_night.id` ou `tab.id` **sem FK**. A
   integridade é garantida pela aplicação, não pelo banco. É o preço de manter a
   fronteira do módulo, e é um preço consciente.
3. O garçom digita o número do quarto para lançar na conta. Como `restaurant` não
   pode consultar `hotel`, o `folio` carrega `reference_code` (número do quarto) e
   `reference_label` (nome do hóspede), escritos pelo `hotel` no check-in. O
   `restaurant` busca folio aberto por `reference_code` e nunca toca em `hotel`.

### 1.2 O folio nasce com a reserva

O `folio` é criado no momento em que a `reservation` é criada, **não** no
check-in. O motivo é o sinal pago pelo portal público: o hóspede paga antes de
chegar, e o pagamento precisa de um folio para ser registrado. Abrir o folio só
no check-in deixaria esse dinheiro sem lugar.

Como efeito colateral positivo, reserva cancelada com sinal pago também tem onde
registrar a retenção ou o estorno.

Por isso `reservation.folio_id` é `NOT NULL`, e `required_deposit_amount` guarda
apenas o valor **exigido**. O que foi de fato pago vive em `payment`, como
qualquer outro dinheiro do sistema.

### 1.3 Invariantes em `CHECK` constraint

Onde a regra é expressável em SQL, ela vai também para o banco. Não substitui a
validação no agregado — é a segunda linha de defesa, para o caso de alguém
escrever direto no banco ou de um bug passar pela aplicação.

Exemplos: item vendido por peso exige `price_per_kilo` e proíbe `unit_price` ·
`check_out_date` maior que `check_in_date` · `AdjustmentCharge` exige autorização
e motivo.

### 1.4 Snapshot de preço

`tab_item` e `room_night` gravam nome e valor praticados no momento do
lançamento. Alterar o cardápio ou a tarifa nunca altera conta passada.

### 1.5 Numeração das migrations é atribuída aqui

Agentes trabalhando em paralelo criando `V3__` ao mesmo tempo geram colisão de
versão no Flyway, que só aparece no merge. Por isso cada task já tem seu número
reservado na seção 3.

### 1.6 Convenções

- `snake_case`, tabela no singular, PK `id UUID`, FK `{tabela}_id`
- Dinheiro: `NUMERIC(12,2)`. Percentual: `NUMERIC(5,4)` (0.1000 = 10%)
- Data/hora: `TIMESTAMPTZ`. Data pura: `DATE`. Hora pura: `TIME`
- Auditoria em toda tabela transacional: `created_at`, `created_by`,
  `updated_at`, `updated_by`
- Enum persistido como `VARCHAR` com `CHECK`, nunca ordinal
- Palavras reservadas evitadas: `app_user`, `dining_table`

---

## 2. Relacionamentos

```
property 1──N  app_user, setting, room_type, room, guest,
               menu_category, menu_item, modifier, dining_table,
               reservation, tab, folio, cash_drawer_session

HOTEL
room_type   1──N room
room_type   1──N rate_plan
room_type   1──N daily_inventory
room_type   1──N reservation
guest       1──N reservation          (titular)
room        0──N reservation          (atribuído no check-in)
reservation 1──N room_night
reservation 1──N reservation_child
reservation N──1 folio                (aberto junto com a reserva)

RESTAURANT
menu_category 1──N menu_item
menu_item     1──N menu_item_variant
menu_item     1──N availability_window
menu_item     N──N modifier           (via menu_item_modifier)
dining_table  1──N tab
tab           1──N tab_item
tab_item      1──N tab_item_modifier
tab           N──1 folio              (definido no fechamento)
tab           N──1 tab                (merge: comanda absorvida por outra)
tab_item      N──1 tab                (transferência: comanda de origem)

BILLING
folio               1──N charge
folio               1──N payment
folio               1──N payment_intent
payment_intent      1──1 payment          (após confirmação do webhook)
cash_drawer_session 1──N cash_movement
cash_drawer_session 1──N payment
```

---

## 3. Migrations e responsáveis

| Versão | Arquivo | Task | Tabelas |
|---|---|---|---|
| V1 | `V1__baseline.sql` | 0.3 | `property`, `setting`, `app_user`, `user_role` |
| V2 | `V2__auth.sql` | 0.4 | altera `app_user` (`username`, `token_version`) |
| V3 | `V3__menu.sql` | 0.8 | `menu_category`, `menu_item`, `menu_item_variant`, `modifier`, `menu_item_modifier`, `availability_window` |
| V4 | `V4__menu_variants_and_name_uniqueness.sql` | 1.2 | altera `menu_item_variant` (`is_available`); nome único ignorando maiúsculas em `menu_category`, `menu_item`, `menu_item_variant`, `modifier` |
| V5 | `V5__dining_table.sql` | 1.5 | `dining_table` |
| V6 | `V6__billing.sql` | 1.3 | `folio`, `charge`, `payment`, `payment_intent` |
| V7 | `V7__tab.sql` | 2.2 | `tab`, `tab_item`, `tab_item_modifier` |
| V8 | `V8__cash.sql` | 2.4 | `cash_drawer_session`, `cash_movement` + FK em `payment` |
| V9 | `V9__hotel_inventory.sql` | 1.1 | `room_type`, `room`, `rate_plan` |
| V10 | `V10__reservation.sql` | 2.1 | `guest`, `daily_inventory`, `reservation`, `reservation_child`, `room_night` |
| V11 | `V11__tab_closing.sql` | 3.2 | altera `tab` (`folio_id`, fechamento, destino, taxa de serviço, `guest_count`) e `tab_item` (`split_group`) |

Renumerada em 2026-09-24 (decisão #5 da task 1.2): o restaurante é construído
antes do hotel, e a versão segue a ordem de execução. Com o hotel no meio, o
Flyway recusaria V5/V7 depois de V6/V8 já aplicadas em qualquer banco.

---

## 4. V1 — baseline

```sql
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "btree_gist";

CREATE TABLE property (
    id              UUID PRIMARY KEY,
    legal_name      VARCHAR(255) NOT NULL,
    trade_name      VARCHAR(255),
    cnpj            VARCHAR(14),
    street          VARCHAR(255),
    city            VARCHAR(120),
    state           VARCHAR(2),
    postal_code     VARCHAR(8),
    time_zone       VARCHAR(50)  NOT NULL DEFAULT 'America/Fortaleza',
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      UUID,
    updated_at      TIMESTAMPTZ,
    updated_by      UUID
);

CREATE TABLE setting (
    id              UUID PRIMARY KEY,
    property_id     UUID         NOT NULL REFERENCES property(id),
    setting_key     VARCHAR(100) NOT NULL,
    setting_value   TEXT         NOT NULL,
    value_type      VARCHAR(20)  NOT NULL
                    CHECK (value_type IN ('STRING','INTEGER','DECIMAL','BOOLEAN','TIME')),
    description     TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      UUID,
    updated_at      TIMESTAMPTZ,
    updated_by      UUID,
    CONSTRAINT uk_setting_key UNIQUE (property_id, setting_key)
);

CREATE TABLE app_user (
    id              UUID PRIMARY KEY,
    property_id     UUID         NOT NULL REFERENCES property(id),
    full_name       VARCHAR(255) NOT NULL,
    email           VARCHAR(255) NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    last_login_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      UUID,
    updated_at      TIMESTAMPTZ,
    updated_by      UUID,
    CONSTRAINT uk_app_user_email UNIQUE (email)
);

CREATE TABLE user_role (
    app_user_id     UUID        NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    role            VARCHAR(30) NOT NULL
                    CHECK (role IN ('ADMIN','FRONT_DESK','WAITER','KITCHEN')),
    PRIMARY KEY (app_user_id, role)
);
```

**Decisão:** não existe tabela `role`. Os papéis são um enum fechado no código;
uma tabela de domínio aqui seria indireção sem ganho. Se um dia houver permissão
granular configurável, aí entra a tabela.

---

## 5. V2 — autenticação

```sql
ALTER TABLE app_user
    ADD COLUMN username      VARCHAR(30),
    ADD COLUMN token_version INTEGER NOT NULL DEFAULT 0;

-- email deixa de ser obrigatório: cozinheiro pode não ter
ALTER TABLE app_user ALTER COLUMN email DROP NOT NULL;

-- unicidade do email só quando presente
ALTER TABLE app_user DROP CONSTRAINT uk_app_user_email;
CREATE UNIQUE INDEX uk_app_user_email ON app_user (email) WHERE email IS NOT NULL;

-- username é o novo campo de login
UPDATE app_user SET username = split_part(email, '@', 1) WHERE username IS NULL;
ALTER TABLE app_user ALTER COLUMN username SET NOT NULL;
CREATE UNIQUE INDEX uk_app_user_username ON app_user (lower(username));
```

**Notas**

- `username` é o novo campo de login (3 a 30 caracteres, letras minúsculas,
  dígitos, ponto e sublinhado). `email` deixa de ser obrigatório: o cozinheiro
  pode não ter um.
- O índice sobre `lower(username)` torna o login **insensível a maiúsculas**
  sem guardar o valor normalizado — `Joao` e `joao` são o mesmo usuário.
- `token_version` sustenta a sessão única: todo token emitido carrega a versão
  vigente no momento da emissão, e login novo ou logout incrementam a coluna,
  invalidando imediatamente qualquer token emitido antes.
- Usuários existentes recebem `username` derivado do prefixo do e-mail
  (`split_part(email, '@', 1)`) para a migration não quebrar sobre dado já
  existente.

---

## 6. V3 — cardápio

```sql
CREATE TABLE menu_category (
    id              UUID PRIMARY KEY,
    property_id     UUID         NOT NULL REFERENCES property(id),
    name            VARCHAR(100) NOT NULL,
    display_order   SMALLINT     NOT NULL DEFAULT 0,
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      UUID,
    updated_at      TIMESTAMPTZ,
    updated_by      UUID,
    CONSTRAINT uk_menu_category_name UNIQUE (property_id, name)
);

CREATE TABLE menu_item (
    id                      UUID PRIMARY KEY,
    property_id             UUID         NOT NULL REFERENCES property(id),
    menu_category_id        UUID         NOT NULL REFERENCES menu_category(id),
    name                    VARCHAR(150) NOT NULL,
    description             TEXT,
    sold_by_weight          BOOLEAN      NOT NULL DEFAULT FALSE,
    unit_price              NUMERIC(12,2),
    price_per_kilo          NUMERIC(12,2),
    prep_station            VARCHAR(20)  NOT NULL
                            CHECK (prep_station IN ('KITCHEN','PIZZA','BAR')),
    service_charge_eligible BOOLEAN      NOT NULL DEFAULT TRUE,
    is_available            BOOLEAN      NOT NULL DEFAULT TRUE,
    is_active               BOOLEAN      NOT NULL DEFAULT TRUE,
    display_order           SMALLINT     NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by              UUID,
    updated_at              TIMESTAMPTZ,
    updated_by              UUID,
    CONSTRAINT uk_menu_item_name UNIQUE (property_id, name),
    CONSTRAINT ck_menu_item_pricing CHECK (
        (sold_by_weight     AND price_per_kilo IS NOT NULL AND unit_price     IS NULL)
     OR (NOT sold_by_weight AND unit_price     IS NOT NULL AND price_per_kilo IS NULL)
    ),
    CONSTRAINT ck_menu_item_price_positive CHECK (
        COALESCE(unit_price, price_per_kilo) > 0
    )
);

CREATE TABLE menu_item_variant (
    id              UUID PRIMARY KEY,
    menu_item_id    UUID         NOT NULL REFERENCES menu_item(id) ON DELETE CASCADE,
    name            VARCHAR(50)  NOT NULL,
    unit_price      NUMERIC(12,2) NOT NULL CHECK (unit_price > 0),
    display_order   SMALLINT     NOT NULL DEFAULT 0,
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      UUID,
    updated_at      TIMESTAMPTZ,
    updated_by      UUID,
    CONSTRAINT uk_variant_name UNIQUE (menu_item_id, name)
);

CREATE TABLE modifier (
    id              UUID PRIMARY KEY,
    property_id     UUID         NOT NULL REFERENCES property(id),
    name            VARCHAR(100) NOT NULL,
    price           NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (price >= 0),
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      UUID,
    updated_at      TIMESTAMPTZ,
    updated_by      UUID,
    CONSTRAINT uk_modifier_name UNIQUE (property_id, name)
);

CREATE TABLE menu_item_modifier (
    menu_item_id    UUID     NOT NULL REFERENCES menu_item(id) ON DELETE CASCADE,
    modifier_id     UUID     NOT NULL REFERENCES modifier(id),
    max_quantity    SMALLINT NOT NULL DEFAULT 1 CHECK (max_quantity > 0),
    PRIMARY KEY (menu_item_id, modifier_id)
);

CREATE TABLE availability_window (
    id              UUID PRIMARY KEY,
    menu_item_id    UUID     NOT NULL REFERENCES menu_item(id) ON DELETE CASCADE,
    day_of_week     SMALLINT CHECK (day_of_week BETWEEN 1 AND 7),
    start_time      TIME     NOT NULL,
    end_time        TIME     NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      UUID,
    updated_at      TIMESTAMPTZ,
    updated_by      UUID
);

CREATE INDEX idx_menu_item_category ON menu_item (property_id, menu_category_id)
    WHERE is_active;
CREATE INDEX idx_availability_window_item ON availability_window (menu_item_id);
```

**Notas**

- `day_of_week` nulo significa "todos os dias". ISO-8601: 1 = segunda.
- `end_time` menor que `start_time` significa janela que cruza a meia-noite
  (22:00 às 02:00). É válido e o agregado precisa tratar.
- Item **sem nenhuma** `availability_window` está disponível a qualquer hora.
  Pizza recebe uma janela `18:30–23:00`.
- `ck_menu_item_pricing` é a invariante mais importante desta migration: o banco
  impede fisicamente um item por peso ter preço unitário.

---

## 7. V6 — billing

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

CREATE TABLE payment_intent (
    id                  UUID PRIMARY KEY,
    property_id         UUID          NOT NULL REFERENCES property(id),
    folio_id            UUID          NOT NULL REFERENCES folio(id),
    purpose             VARCHAR(20)   NOT NULL
                        CHECK (purpose IN ('TAB_QR','RESERVATION_DEPOSIT')),
    amount              NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    method              VARCHAR(20)   NOT NULL
                        CHECK (method IN ('PIX','CREDIT_CARD','DEBIT_CARD')),
    status              VARCHAR(20)   NOT NULL DEFAULT 'CREATED'
                        CHECK (status IN ('CREATED','AWAITING_PAYMENT','CONFIRMED',
                                          'EXPIRED','FAILED','CANCELLED')),
    provider            VARCHAR(40)   NOT NULL,
    provider_intent_id  VARCHAR(255),
    qr_payload          TEXT,
    expires_at          TIMESTAMPTZ   NOT NULL,
    confirmed_at        TIMESTAMPTZ,
    failure_reason      TEXT,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by          UUID,
    updated_at          TIMESTAMPTZ,
    updated_by          UUID,
    CONSTRAINT uk_payment_intent_provider UNIQUE (provider, provider_intent_id),
    CONSTRAINT ck_payment_intent_confirmed CHECK (
        status <> 'CONFIRMED' OR confirmed_at IS NOT NULL
    )
);

ALTER TABLE payment
    ADD CONSTRAINT fk_payment_intent
    FOREIGN KEY (payment_intent_id) REFERENCES payment_intent(id);

CREATE INDEX idx_charge_folio   ON charge (folio_id);
CREATE INDEX idx_payment_folio  ON payment (folio_id);
CREATE UNIQUE INDEX idx_folio_open_ref ON folio (property_id, reference_code)
    WHERE status = 'OPEN' AND folio_type = 'STAY';
CREATE INDEX idx_intent_expiring
    ON payment_intent (expires_at) WHERE status IN ('CREATED','AWAITING_PAYMENT');
CREATE INDEX idx_intent_folio ON payment_intent (folio_id);
```

**Notas**

- `charge` usa herança `SINGLE_TABLE` com `charge_type` como discriminador,
  mapeando `RoomNightCharge`, `TabCharge` e `AdjustmentCharge`.
- `amount` **não** tem `CHECK > 0`: `AdjustmentCharge` pode ser negativo
  (desconto) e o estorno é o lançamento oposto, negativo. Tem `CHECK <> 0`:
  lançamento de valor zero não existe.
- `source_id` sem FK é intencional (ver seção 1.1).
- `folio.owner_id` é o `FolioOwner` do `billing/api` (#1 da 0.6), sem FK pelo
  mesmo motivo: `STAY` aponta para a reserva, `TAB` para a comanda.
  `uk_folio_owner` garante um folio por dono (task 1.3).
- `ck_folio_stay_reference`: folio `STAY` sempre tem `FolioReference`; folio
  `TAB` não tem.
- `charge` não tem `posted_at`/`posted_by`: o autor do lançamento é
  `created_by` (#9 da 0.6), e o momento, `created_at`.
- `reversal_of_charge_id` aponta o lançamento que o estorno desfaz. Único: um
  lançamento se estorna uma vez só. O estorno exige `reason`.
- `payment.refunded_*` registram o estorno de pagamento (`REFUNDED`), que
  deixa de abater o saldo (task 1.3).
- `payment.cash_drawer_session_id` nasce nulo e sem FK; a V8 (task 2.4) cria a
  FK. `payment.payment_intent_id` fica sempre nulo até a v1.1.
- `idempotency_key` único é o que impede o duplo clique do caixa gerar dois
  pagamentos.
- `idx_folio_open_ref` é o índice que o `restaurant` usa para achar o folio pelo
  número do quarto. É **único**: dois folios `STAY` abertos com o mesmo código
  deixariam a busca ambígua (#17 da 1.3).

**Sobre `payment_intent`:** é o registro intermediário do pagamento online, tanto
do QR code da comanda quanto do sinal da reserva. Três pontos de desenho:

1. **A confirmação vem do webhook do gateway, nunca do cliente.** A `Tab` ou a
   `Reservation` só são quitadas quando `status` vira `CONFIRMED`. Cliente
   dizendo "já paguei" não muda nada.
2. `uk_payment_intent_provider` é a **idempotência do webhook**. Gateway reenvia
   o mesmo evento com frequência, e sem essa unicidade um Pix vira dois
   pagamentos.
3. `expires_at` mais `idx_intent_expiring` sustentam o job que expira cobrança
   não paga — devolvendo a comanda de `CLOSING` para `OPEN` e liberando o
   inventário da reserva.

O `payment` só nasce quando o intent confirma. Por isso `received_by` virou
nulável: pagamento por QR code não tem operador, e a constraint
`ck_payment_operator` garante que existe ou um operador ou um intent.

---

## 8. V9 — inventário do hotel

```sql
CREATE TABLE room_type (
    id                  UUID PRIMARY KEY,
    property_id         UUID         NOT NULL REFERENCES property(id),
    name                VARCHAR(100) NOT NULL,
    description         TEXT,
    max_occupancy       SMALLINT     NOT NULL CHECK (max_occupancy > 0),
    standard_occupancy  SMALLINT     NOT NULL CHECK (standard_occupancy > 0),
    is_active           BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by          UUID,
    updated_at          TIMESTAMPTZ,
    updated_by          UUID,
    CONSTRAINT uk_room_type_name UNIQUE (property_id, name),
    CONSTRAINT ck_room_type_occupancy CHECK (standard_occupancy <= max_occupancy)
);

CREATE TABLE room (
    id              UUID PRIMARY KEY,
    property_id     UUID        NOT NULL REFERENCES property(id),
    room_type_id    UUID        NOT NULL REFERENCES room_type(id),
    room_number     VARCHAR(20) NOT NULL,
    floor           VARCHAR(20),
    status          VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE'
                    CHECK (status IN ('AVAILABLE','OCCUPIED','MAINTENANCE')),
    notes           TEXT,
    is_active       BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      UUID,
    updated_at      TIMESTAMPTZ,
    updated_by      UUID,
    CONSTRAINT uk_room_number UNIQUE (property_id, room_number)
);

CREATE TABLE rate_plan (
    id                  UUID PRIMARY KEY,
    property_id         UUID          NOT NULL REFERENCES property(id),
    room_type_id        UUID          NOT NULL REFERENCES room_type(id),
    name                VARCHAR(100)  NOT NULL,
    valid_from          DATE          NOT NULL,
    valid_to            DATE          NOT NULL,
    single_rate         NUMERIC(12,2) NOT NULL CHECK (single_rate  >= 0),
    double_rate         NUMERIC(12,2) NOT NULL CHECK (double_rate  >= 0),
    extra_guest_rate    NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (extra_guest_rate >= 0),
    is_active           BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by          UUID,
    updated_at          TIMESTAMPTZ,
    updated_by          UUID,
    CONSTRAINT ck_rate_plan_period CHECK (valid_to >= valid_from),
    CONSTRAINT ex_rate_plan_no_overlap EXCLUDE USING gist (
        room_type_id WITH =,
        daterange(valid_from, valid_to, '[]') WITH &&
    ) WHERE (is_active)
);

CREATE INDEX idx_room_by_type   ON room (property_id, room_type_id) WHERE is_active;
CREATE INDEX idx_room_available ON room (property_id, status)       WHERE is_active;
```

**Nota sobre `ex_rate_plan_no_overlap`:** é uma constraint de exclusão do
Postgres (exige `btree_gist`, criada na V1). Ela impede fisicamente cadastrar
duas tarifas ativas com períodos sobrepostos para o mesmo tipo de quarto. Sem
isso, o cálculo da diária ficaria ambíguo e o bug só apareceria na virada de
temporada.

---

## 9. V5 — mesas

```sql
CREATE TABLE dining_table (
    id              UUID PRIMARY KEY,
    property_id     UUID        NOT NULL REFERENCES property(id),
    label           VARCHAR(20) NOT NULL,
    seats           SMALLINT    CHECK (seats > 0),
    area            VARCHAR(50),
    is_active       BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      UUID,
    updated_at      TIMESTAMPTZ,
    updated_by      UUID,
    CONSTRAINT uk_dining_table_label UNIQUE (property_id, label)
);
```

---

## 10. V10 — reservas

```sql
CREATE TABLE guest (
    id              UUID PRIMARY KEY,
    property_id     UUID         NOT NULL REFERENCES property(id),
    full_name       VARCHAR(255) NOT NULL,
    document_type   VARCHAR(15)  NOT NULL
                    CHECK (document_type IN ('CPF','PASSPORT')),
    document_number VARCHAR(30)  NOT NULL,
    email           VARCHAR(255),
    phone           VARCHAR(30),
    birth_date      DATE,
    nationality     VARCHAR(60),
    street          VARCHAR(255),
    city            VARCHAR(120),
    state           VARCHAR(60),
    country         VARCHAR(60),
    postal_code     VARCHAR(20),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      UUID,
    updated_at      TIMESTAMPTZ,
    updated_by      UUID,
    CONSTRAINT uk_guest_document UNIQUE (property_id, document_type, document_number)
);

CREATE TABLE daily_inventory (
    id              UUID PRIMARY KEY,
    property_id     UUID     NOT NULL REFERENCES property(id),
    room_type_id    UUID     NOT NULL REFERENCES room_type(id),
    stay_date       DATE     NOT NULL,
    total_rooms     SMALLINT NOT NULL CHECK (total_rooms  >= 0),
    booked_rooms    SMALLINT NOT NULL DEFAULT 0 CHECK (booked_rooms >= 0),
    CONSTRAINT uk_daily_inventory UNIQUE (property_id, room_type_id, stay_date)
);

CREATE TABLE reservation (
    id                        UUID PRIMARY KEY,
    property_id               UUID          NOT NULL REFERENCES property(id),
    confirmation_code         VARCHAR(10)   NOT NULL,
    room_type_id              UUID          NOT NULL REFERENCES room_type(id),
    primary_guest_id          UUID          NOT NULL REFERENCES guest(id),
    assigned_room_id          UUID          REFERENCES room(id),
    folio_id                  UUID          NOT NULL REFERENCES folio(id),
    check_in_date             DATE          NOT NULL,
    check_out_date            DATE          NOT NULL,
    adults                    SMALLINT      NOT NULL CHECK (adults > 0),
    status                    VARCHAR(20)   NOT NULL DEFAULT 'PENDING'
                              CHECK (status IN ('PENDING','CONFIRMED','CHECKED_IN',
                                                'CHECKED_OUT','CANCELLED','NO_SHOW')),
    channel                   VARCHAR(20)   NOT NULL DEFAULT 'DIRECT'
                              CHECK (channel IN ('DIRECT','WEB','PHONE','OTA')),
    external_reservation_id   VARCHAR(100),
    required_deposit_amount   NUMERIC(12,2) NOT NULL DEFAULT 0
                              CHECK (required_deposit_amount >= 0),
    overbooking_authorized_by UUID,
    expires_at                TIMESTAMPTZ,
    confirmed_at              TIMESTAMPTZ,
    checked_in_at             TIMESTAMPTZ,
    checked_out_at            TIMESTAMPTZ,
    cancelled_at              TIMESTAMPTZ,
    cancellation_reason       TEXT,
    no_show_at                TIMESTAMPTZ,
    notes                     TEXT,
    created_at                TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by                UUID,
    updated_at                TIMESTAMPTZ,
    updated_by                UUID,
    CONSTRAINT uk_reservation_code UNIQUE (confirmation_code),
    CONSTRAINT ck_reservation_period CHECK (check_out_date > check_in_date),
    CONSTRAINT ck_reservation_room CHECK (
        status NOT IN ('CHECKED_IN','CHECKED_OUT') OR assigned_room_id IS NOT NULL
    ),
    CONSTRAINT ck_reservation_cancelled CHECK (
        status <> 'CANCELLED' OR cancelled_at IS NOT NULL
    )
);

CREATE TABLE reservation_child (
    id              UUID     PRIMARY KEY,
    reservation_id  UUID     NOT NULL REFERENCES reservation(id) ON DELETE CASCADE,
    age             SMALLINT NOT NULL CHECK (age BETWEEN 0 AND 17)
);

CREATE TABLE room_night (
    id                  UUID PRIMARY KEY,
    reservation_id      UUID          NOT NULL REFERENCES reservation(id) ON DELETE CASCADE,
    rate_plan_id        UUID          REFERENCES rate_plan(id),
    stay_date           DATE          NOT NULL,
    base_amount         NUMERIC(12,2) NOT NULL CHECK (base_amount      >= 0),
    extra_guest_amount  NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (extra_guest_amount >= 0),
    children_amount     NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (children_amount    >= 0),
    total_amount        NUMERIC(12,2) NOT NULL CHECK (total_amount     >= 0),
    CONSTRAINT uk_room_night UNIQUE (reservation_id, stay_date)
);

CREATE UNIQUE INDEX idx_daily_inventory_lookup
    ON daily_inventory (property_id, room_type_id, stay_date);
CREATE INDEX idx_reservation_arrivals
    ON reservation (property_id, check_in_date)  WHERE status = 'CONFIRMED';
CREATE INDEX idx_reservation_departures
    ON reservation (property_id, check_out_date) WHERE status = 'CHECKED_IN';
CREATE INDEX idx_reservation_expiring
    ON reservation (expires_at) WHERE status = 'PENDING';
```

**Notas**

- `daily_inventory` **não** tem `CHECK (booked_rooms <= total_rooms)`, porque
  overbooking autorizado precisa ultrapassar. O limite é aplicado no `UPDATE`
  condicional, que é onde a autorização é conhecida.
- `reservation_child` guarda a idade na data do check-in, conforme decidido.
  Quando o cliente exigir a ficha de registro completa (FNRH), entra uma tabela
  `reservation_occupant` nova — nenhuma coluna existente muda.
- `expires_at` é o prazo da pré-reserva aguardando sinal. O job de expiração usa
  `idx_reservation_expiring`.
- `ck_reservation_room` garante no banco que não existe hóspede em check-in sem
  quarto atribuído.
- `folio_id` é `NOT NULL`: toda reserva nasce com um folio, mesmo antes do
  check-in, para que o sinal pago pelo portal tenha onde ser registrado.
- `required_deposit_amount` é o valor **exigido**, não o pago. O valor pago é a
  soma dos `payment` do folio. Guardar o pago aqui criaria um número que pode
  divergir da verdade.
- `reservation` **não** guarda valor total. O total é derivado — soma de
  `room_night.total_amount` — e calculado por `Reservation.totalAmount()`.
- `room_night.total_amount` **é** guardado: é o snapshot do valor cobrado
  naquela noite (seção 1.4), não um valor derivado.

---

## 11. V7 — comandas

Escrita pela task 2.2 (`docs/task-2.2-tab.md`, decisões #1, #2, #9, #10, #12).
Fechamento, destino, taxa de serviço, `folio_id`, `guest_count` e `split_group`
**não** estão na V7: chegam com a `V11__tab_closing.sql` da task 3.2 (seção 11.1).
As colunas do KDS (3.5) e de transferência e junção (3.6) já nascem aqui, sem
mapeamento até a task delas, para evitar `ALTER` concorrente na Onda 3. A exceção
é `delivered_at`: o item vendido por peso nasce `DELIVERED` (#9) e a 2.2 já o grava.

```sql
-- Task 2.2 - tabs: opening, items and cancellation.
-- Closing, service charge, destination, folio_id and split_group arrive with
-- task 3.2 (V11). KDS timestamps (3.5) and transfer and merge columns (3.6) are
-- created here, unmapped until their task, except delivered_at: an item sold
-- by weight is born DELIVERED (decision #9), so task 2.2 already writes it.

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

**Notas**

- Os dois índices únicos parciais são a defesa real contra abrir duas comandas na
  mesma mesa ou reutilizar um cartão em uso (#1). Constraint normal não resolve,
  porque a mesa pode ter várias comandas fechadas no histórico. A aplicação não
  consulta antes de abrir: grava, e traduz a violação de `idx_tab_open_by_table`
  e `idx_tab_open_by_card` em 409. Comanda `CANCELLED` libera a mesa e o cartão.
- `item_name`, `variant_name`, `unit_price`, `price_per_kilo`, `prep_station` e,
  em `tab_item_modifier`, `modifier_name` e `price` são snapshots.
- `line_total` é calculado uma vez no lançamento e nunca muda: por unidade,
  `(unit_price + Σ price × quantity dos adicionais) × quantity`; por peso,
  `weight_grams / 1000 × price_per_kilo`, centavo arredondado meio para cima.
- `ck_tab_item_pricing`: item por peso tem quantidade 1, sem variação e sem
  `unit_price`; item por unidade não tem `weight_grams` nem `price_per_kilo`.
- `service_chargeable` é resolvido na inclusão do item, combinando `tab.origin`
  com `menu_item.service_charge_eligible`.
- `tab_item_modifier` tem PK composta `(tab_item_id, modifier_id)`, no padrão da
  `menu_item_modifier`: o mesmo adicional não se repete no item.
- `tab_item.tab_id` não tem `ON DELETE CASCADE`: comanda nunca é apagada, só
  cancelada (`cancelled_*`, #10). Item cancelado também fica para sempre.
- `idx_kds_queue` é o índice que sustenta as três telas de KDS.

**Transferência de item.** Mover um item entre comandas troca `tab_id` e grava
`transferred_from_tab_id` com a origem. Sem esse rastro, ninguém consegue depois
explicar por que a mesa 4 fechou com menos do que foi lançado nela.

**Junção de comandas.** A comanda absorvida recebe status `MERGED` e
`merged_into_tab_id` apontando para a que ficou. Os itens migram com
`transferred_from_tab_id` preenchido. A comanda absorvida nunca é apagada — ela
some do salão porque o índice único parcial só considera `OPEN` e `CLOSING`, mas
o histórico permanece.

### 11.1 V11 — fechamento da comanda (task 3.2)

O que saiu da V7 e entra na `V11__tab_closing.sql`, desenho original a revisar
na spec da 3.2:

```sql
ALTER TABLE tab
    ADD COLUMN folio_id               UUID         REFERENCES folio(id),
    ADD COLUMN destination            VARCHAR(20)
                                      CHECK (destination IN ('DIRECT_PAYMENT','ROOM_ACCOUNT')),
    ADD COLUMN service_charge_applied BOOLEAN      NOT NULL DEFAULT TRUE,
    ADD COLUMN service_charge_rate    NUMERIC(5,4) NOT NULL DEFAULT 0.1000,
    ADD COLUMN guest_count            SMALLINT     CHECK (guest_count > 0),
    ADD COLUMN closing_started_at     TIMESTAMPTZ,
    ADD COLUMN closed_at              TIMESTAMPTZ,
    ADD COLUMN closed_by              UUID,
    ADD CONSTRAINT ck_tab_closed CHECK (
        status <> 'CLOSED'
        OR (closed_at IS NOT NULL AND closed_by IS NOT NULL AND folio_id IS NOT NULL)
    );

ALTER TABLE tab_item
    ADD COLUMN split_group SMALLINT NOT NULL DEFAULT 1 CHECK (split_group > 0);
```

**Divisão de conta por item.** `split_group` agrupa os itens por pagante: todos
em 1 por padrão, e no fechamento o operador redistribui. Cada grupo distinto
vira um `Charge` separado no folio, e cada `Charge` é quitado independentemente.
Divisão por valor igual continua sendo simplesmente N `payment` sobre o mesmo
folio, sem tocar em `split_group`.

---

## 12. V8 — caixa

```sql
CREATE TABLE cash_drawer_session (
    id              UUID PRIMARY KEY,
    property_id     UUID          NOT NULL REFERENCES property(id),
    opened_by       UUID          NOT NULL REFERENCES app_user(id),
    opened_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    opening_float   NUMERIC(12,2) NOT NULL CHECK (opening_float >= 0),
    closed_by       UUID          REFERENCES app_user(id),
    closed_at       TIMESTAMPTZ,
    expected_amount NUMERIC(12,2),
    counted_amount  NUMERIC(12,2),
    difference      NUMERIC(12,2),
    status          VARCHAR(10)   NOT NULL DEFAULT 'OPEN'
                    CHECK (status IN ('OPEN','CLOSED')),
    CONSTRAINT ck_cash_session_closed CHECK (
        status <> 'CLOSED'
        OR (closed_at IS NOT NULL AND closed_by IS NOT NULL
            AND counted_amount IS NOT NULL)
    )
);

CREATE TABLE cash_movement (
    id                      UUID PRIMARY KEY,
    cash_drawer_session_id  UUID          NOT NULL
                            REFERENCES cash_drawer_session(id) ON DELETE CASCADE,
    movement_type           VARCHAR(20)   NOT NULL
                            CHECK (movement_type IN ('OPENING_FLOAT','CASH_DROP',
                                                     'CASH_SUPPLY','CLOSING_COUNT')),
    amount                  NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    reason                  TEXT,
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by              UUID          NOT NULL
);

ALTER TABLE payment
    ADD CONSTRAINT fk_payment_cash_session
    FOREIGN KEY (cash_drawer_session_id) REFERENCES cash_drawer_session(id);

CREATE UNIQUE INDEX idx_cash_session_open
    ON cash_drawer_session (property_id)
    WHERE status = 'OPEN';

CREATE INDEX idx_cash_movement_session ON cash_movement (cash_drawer_session_id);
```

**Nota:** `idx_cash_session_open` permite **um** turno de caixa aberto por
propriedade. Se depois houver mais de um ponto de caixa simultâneo, o índice
passa a incluir a coluna do terminal. Vale confirmar com o cliente se hoje existe
mais de um caixa operando ao mesmo tempo.

---

## 13. O que o banco não garante

A integridade abaixo é responsabilidade exclusiva da aplicação:

| Regra | Por que não está no banco |
|---|---|
| `charge.source_id` aponta para registro existente | FK cruzaria fronteira de módulo |
| `folio.balance()` zerado antes de fechar | Depende de soma de duas tabelas |
| Transição válida na máquina de estados | `CHECK` não vê o valor anterior |
| `booked_rooms` não passa de `total_rooms` sem autorização | O `UPDATE` condicional conhece a autorização; a constraint não |
| `line_total` bate com preço × quantidade + modifiers | Cálculo com agregação de outra tabela |
| Item fora da `availability_window` | Depende do horário do pedido |
| Item transferido não volta para comanda fechada | Depende do status da comanda de destino |
| Junção não cria ciclo entre comandas | `CHECK` só barra o auto-merge direto |
| `payment_intent` confirmado gera exatamente um `payment` | Regra de duas tabelas, garantida pelo webhook |
| Soma dos `Charge` de um `split_group` bate com os itens | Agregação entre módulos |

Cada uma delas precisa de teste de unidade no agregado correspondente. São
justamente os pontos onde um bug não é barrado pelo banco.

---

## 14. Pendências

| Pendência | Efeito |
|---|---|
| Tipos de quarto reais e ocupação máxima | Só cadastro, o schema não muda |
| Ficha de registro completa (FNRH) | Tabela `reservation_occupant` nova, sem alterar colunas |
| Mais de um caixa simultâneo | Altera `idx_cash_session_open` |
| Gateway de pagamento | `payment_intent.provider` e `provider_intent_id` já preparados; falta escolher o fornecedor |
| Documento fiscal | Tabelas do módulo `tax-invoice`, ainda não desenhadas |

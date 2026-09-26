-- Task 1.3 - the folio: charges, reversals and payments.
--
-- docs/schema-banco-de-dados.md section 7, as updated by this task (decision #13).

-- One folio per owner (FolioOwner of billing/api, decision #1 of task 0.6):
-- a STAY folio belongs to a reservation, a TAB folio to a tab. owner_id has no
-- foreign key on purpose: billing never points at hotel or restaurant.
-- A STAY folio always carries its FolioReference (room number and label).
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

-- Append-only. The author of a charge is created_by (decision #9 of task 0.6).
-- A reversal is a charge of its own, of the opposite amount, pointing at the
-- charge it undoes; uk_charge_reversal allows one reversal per charge.
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

-- A refunded payment (decision #2) keeps its row and stops counting towards
-- the balance. cash_drawer_session_id is born null and without a foreign key:
-- the V8 (task 2.4) creates it. payment_intent_id stays null until v1.1.
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
    cash_drawer_session_id  UUID,
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

-- Decision #14: the table of the online payment is born now, with no code
-- until v1.1.
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

-- Decision #17: unique, or two open stays under the same room number would
-- leave the lookup by room ambiguous.
CREATE UNIQUE INDEX idx_folio_open_ref ON folio (property_id, reference_code)
    WHERE status = 'OPEN' AND folio_type = 'STAY';

CREATE INDEX idx_intent_expiring
    ON payment_intent (expires_at) WHERE status IN ('CREATED','AWAITING_PAYMENT');
CREATE INDEX idx_intent_folio ON payment_intent (folio_id);

-- Task 2.4 - cash drawer sessions: opening, drops, supplies and closing.
-- One open session per property (decision C1 of task 2.4). The opening float and
-- the counted amount are columns of the session, not movements (C3); the
-- difference is derived, never stored. payment.cash_drawer_session_id was born
-- in V6 without a foreign key (decision #14 of task 1.3); it gets one here.

CREATE TABLE cash_drawer_session (
    id              UUID PRIMARY KEY,
    property_id     UUID          NOT NULL REFERENCES property(id),
    status          VARCHAR(10)   NOT NULL DEFAULT 'OPEN'
                    CHECK (status IN ('OPEN','CLOSED')),
    opened_by       UUID          NOT NULL,
    opened_at       TIMESTAMPTZ   NOT NULL,
    opening_float   NUMERIC(12,2) NOT NULL CHECK (opening_float >= 0),
    closed_by       UUID,
    closed_at       TIMESTAMPTZ,
    expected_amount NUMERIC(12,2),
    counted_amount  NUMERIC(12,2) CHECK (counted_amount >= 0),
    closing_note    TEXT,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by      UUID,
    updated_at      TIMESTAMPTZ,
    updated_by      UUID,
    CONSTRAINT ck_cash_session_closed CHECK (
        status <> 'CLOSED'
        OR (closed_at IS NOT NULL AND closed_by IS NOT NULL
            AND expected_amount IS NOT NULL AND counted_amount IS NOT NULL)
    )
);

CREATE TABLE cash_movement (
    id                      UUID PRIMARY KEY,
    cash_drawer_session_id  UUID          NOT NULL REFERENCES cash_drawer_session(id),
    movement_type           VARCHAR(20)   NOT NULL
                            CHECK (movement_type IN ('CASH_DROP','CASH_SUPPLY')),
    amount                  NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    reason                  TEXT          NOT NULL,
    idempotency_key         VARCHAR(100)  NOT NULL,
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by              UUID          NOT NULL,
    updated_at              TIMESTAMPTZ,
    updated_by              UUID,
    CONSTRAINT uk_cash_movement_idempotency UNIQUE (idempotency_key)
);

ALTER TABLE payment
    ADD CONSTRAINT fk_payment_cash_session
        FOREIGN KEY (cash_drawer_session_id) REFERENCES cash_drawer_session(id),
    ADD CONSTRAINT ck_payment_cash_session
        CHECK (cash_drawer_session_id IS NULL OR method = 'CASH');

CREATE UNIQUE INDEX idx_cash_session_open
    ON cash_drawer_session (property_id) WHERE status = 'OPEN';
CREATE INDEX idx_cash_movement_session ON cash_movement (cash_drawer_session_id);
CREATE INDEX idx_payment_cash_session
    ON payment (cash_drawer_session_id) WHERE cash_drawer_session_id IS NOT NULL;

-- Cash control is optional (C2). Seeded for the properties that already exist;
-- on an empty database the dev seeder seeds it for the property it creates (G3).
INSERT INTO setting (id, property_id, setting_key, setting_value, value_type, description)
SELECT gen_random_uuid(), id, 'billing.cash-drawer.required', 'false', 'BOOLEAN',
       'CASH payments require an open cash drawer session'
  FROM property
ON CONFLICT (property_id, setting_key) DO NOTHING;

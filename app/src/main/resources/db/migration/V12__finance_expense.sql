-- Task F1 - money going out.
--
-- Two dates, because profit and cash flow are different numbers (decision D1):
-- accrual_date says which month the expense belongs to, paid_at says when the
-- money actually left. The rent paid on the 5th is October's expense and the
-- 5th's cash. An expense with paid_at null is an account payable.
--
-- Nothing is ever deleted, as everywhere else in this system: a mistake is
-- cancelled with a reason and stays.

CREATE TABLE expense (
    id                      UUID PRIMARY KEY,
    property_id             UUID          NOT NULL REFERENCES property(id),
    category                VARCHAR(20)   NOT NULL
                            CHECK (category IN ('PAYROLL','SUPPLIER','RENT','UTILITIES',
                                                'TAX','MAINTENANCE','WITHDRAWAL','OTHER')),
    description             VARCHAR(200)  NOT NULL,
    amount                  NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    accrual_date            DATE          NOT NULL,
    due_date                DATE,
    paid_at                 TIMESTAMPTZ,
    paid_by                 UUID,
    method                  VARCHAR(20)
                            CHECK (method IN ('CASH','PIX','CREDIT_CARD','DEBIT_CARD','ROOM_ACCOUNT')),
    supplier_name           VARCHAR(200),
    employee_id             UUID,
    cash_drawer_session_id  UUID          REFERENCES cash_drawer_session(id),
    cancelled_at            TIMESTAMPTZ,
    cancelled_by            UUID,
    cancellation_reason     TEXT,
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by              UUID,
    updated_at              TIMESTAMPTZ,
    updated_by              UUID,

    -- Paid means all three: the moment, the author and the method.
    CONSTRAINT ck_expense_paid CHECK (
        paid_at IS NULL
        OR (paid_by IS NOT NULL AND method IS NOT NULL)
    ),
    -- Cash leaves the drawer, so a cash payment names the session (decision D4).
    CONSTRAINT ck_expense_cash_session CHECK (
        method IS DISTINCT FROM 'CASH' OR cash_drawer_session_id IS NOT NULL
    ),
    -- A cancellation carries its author and reason, like everywhere else.
    CONSTRAINT ck_expense_cancelled CHECK (
        cancelled_at IS NULL
        OR (cancelled_by IS NOT NULL AND cancellation_reason IS NOT NULL)
    ),
    -- A cancelled expense was never paid: cancel first, or reverse the payment.
    CONSTRAINT ck_expense_not_paid_and_cancelled CHECK (
        cancelled_at IS NULL OR paid_at IS NULL
    ),
    -- Payroll names the employee; nothing else does.
    CONSTRAINT ck_expense_employee CHECK (
        employee_id IS NULL OR category = 'PAYROLL'
    )
);

-- The result of a period: expenses by accrual date.
CREATE INDEX idx_expense_accrual ON expense (property_id, accrual_date)
    WHERE cancelled_at IS NULL;

-- The cash flow: expenses by the day the money left.
CREATE INDEX idx_expense_paid ON expense (property_id, paid_at)
    WHERE paid_at IS NOT NULL AND cancelled_at IS NULL;

-- Accounts payable, by due date: what is owed and not yet paid.
CREATE INDEX idx_expense_payable ON expense (property_id, due_date)
    WHERE paid_at IS NULL AND cancelled_at IS NULL;

-- What each category weighs on the revenue.
CREATE INDEX idx_expense_category ON expense (property_id, category, accrual_date)
    WHERE cancelled_at IS NULL;

-- Decision D4: cash paid out of the drawer is a movement of the session, so the
-- expected amount drops with it and the blind count still reconciles. Without
-- this the closing would report a shortfall, and a shortfall looks like theft.
ALTER TABLE cash_movement
    DROP CONSTRAINT cash_movement_movement_type_check;

ALTER TABLE cash_movement
    ADD CONSTRAINT cash_movement_movement_type_check
    CHECK (movement_type IN ('CASH_DROP','CASH_SUPPLY','EXPENSE_PAYMENT'));

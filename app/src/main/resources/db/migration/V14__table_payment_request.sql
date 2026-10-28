-- Task qr-table-payment - the table pays its items by Pix from the phone.
--
-- A request reserves the units the customer chose and carries the amount frozen at
-- creation (decision #20): the units plus, when the customer chose it (decision #38),
-- the service charge on the ones not waived, rounded once. The waiter confirms it after
-- seeing the receipt (decision #8). Units of a line are paid separately (decision #39).

CREATE TABLE table_payment_request (
    id                   UUID PRIMARY KEY,
    property_id          UUID          NOT NULL REFERENCES property(id),
    dining_table_id      UUID          NOT NULL REFERENCES dining_table(id),
    tab_id               UUID          NOT NULL REFERENCES tab(id),
    status               VARCHAR(20)   NOT NULL
                         CHECK (status IN ('PENDING','CONFIRMED','REJECTED','EXPIRED')),
    subtotal             NUMERIC(12,2) NOT NULL CHECK (subtotal > 0),
    service_charge       NUMERIC(12,2) NOT NULL CHECK (service_charge >= 0),
    service_charge_rate  NUMERIC(5,4)  NOT NULL CHECK (service_charge_rate >= 0),
    include_service_charge BOOLEAN     NOT NULL,
    customer_name        VARCHAR(40)   NOT NULL,
    customer_device_id   VARCHAR(64)   NOT NULL,
    pix_payload          TEXT          NOT NULL,
    requested_at         TIMESTAMPTZ   NOT NULL,
    expires_at           TIMESTAMPTZ   NOT NULL,
    resolved_at          TIMESTAMPTZ,
    resolved_by          UUID,
    payment_id           UUID,
    part_charge_id       UUID,
    refunded_at          TIMESTAMPTZ,
    refunded_by          UUID,
    refund_reason        TEXT,
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by           UUID,
    updated_at           TIMESTAMPTZ,
    updated_by           UUID,
    CONSTRAINT ck_table_payment_request_window CHECK (expires_at > requested_at),
    CONSTRAINT ck_table_payment_request_resolution CHECK (
        (status = 'PENDING'   AND resolved_at IS NULL AND resolved_by IS NULL AND payment_id IS NULL)
     OR (status = 'CONFIRMED' AND resolved_at IS NOT NULL AND resolved_by IS NOT NULL
            AND payment_id IS NOT NULL AND part_charge_id IS NOT NULL)
     OR (status = 'REJECTED'  AND resolved_at IS NOT NULL AND resolved_by IS NOT NULL AND payment_id IS NULL)
     OR (status = 'EXPIRED'   AND resolved_at IS NOT NULL AND resolved_by IS NULL AND payment_id IS NULL)
    ),
    CONSTRAINT ck_table_payment_request_refund CHECK (
        (refunded_at IS NULL AND refunded_by IS NULL AND refund_reason IS NULL)
     OR (status = 'CONFIRMED' AND refunded_at IS NOT NULL AND refunded_by IS NOT NULL
            AND refund_reason IS NOT NULL)
    )
);

-- The floor's queue and the expiry job: pending ones, oldest first.
CREATE INDEX idx_table_payment_request_pending
    ON table_payment_request (property_id, requested_at)
    WHERE status = 'PENDING';

CREATE INDEX idx_table_payment_request_by_tab ON table_payment_request (tab_id);

-- The items of a request, with how many units, what they cost and their share of the
-- request's service charge, by the largest remainder (decisions #26, #42 and #45).
-- Kept after a rejection, an expiry or a refund: the request says what it was for, and
-- releasing or refunding subtracts exactly this from the item.
CREATE TABLE table_payment_request_item (
    table_payment_request_id UUID          NOT NULL REFERENCES table_payment_request(id),
    tab_item_id              UUID          NOT NULL REFERENCES tab_item(id),
    quantity                 SMALLINT      NOT NULL CHECK (quantity > 0),
    line_total               NUMERIC(12,2) NOT NULL CHECK (line_total >= 0),
    service_charge           NUMERIC(12,2) NOT NULL CHECK (service_charge >= 0),
    PRIMARY KEY (table_payment_request_id, tab_item_id)
);

-- How many units of the line are paid and how many are held by a request waiting for
-- the waiter, and the sum of the frozen service shares of the paid ones (decision #42).
-- The customer who ordered it from the phone, null on an item the waiter ordered
-- (decision #46).
ALTER TABLE tab_item
    ADD COLUMN paid_quantity       SMALLINT      NOT NULL DEFAULT 0 CHECK (paid_quantity >= 0),
    ADD COLUMN reserved_quantity   SMALLINT      NOT NULL DEFAULT 0 CHECK (reserved_quantity >= 0),
    ADD COLUMN paid_service_charge NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (paid_service_charge >= 0),
    ADD COLUMN customer_name       VARCHAR(40),
    ADD COLUMN customer_device_id  VARCHAR(64),
    ADD CONSTRAINT ck_tab_item_payment_units CHECK (paid_quantity + reserved_quantity <= quantity);

-- Who ordered from the phone (decision #46). Nullable: rows written under V13 have
-- neither; the aggregate requires both on every new request (decision #47).
ALTER TABLE table_order_request
    ADD COLUMN customer_name      VARCHAR(40),
    ADD COLUMN customer_device_id VARCHAR(64);

-- One request waiting per device, no longer one per table (decision #62): several
-- customers of the same table order at once, each under their own name. The cap of 10
-- waiting per table is counted by the submission under the lock of the table row
-- (decision #64). A request from before V14 has no device and stays out of the index.
DROP INDEX idx_table_order_request_pending_by_table;
CREATE UNIQUE INDEX idx_table_order_request_pending_by_device
    ON table_order_request (dining_table_id, customer_device_id)
    WHERE status = 'PENDING';

-- A closing with nothing left to pay posts no charge (decision #19), so a CLOSING or
-- CLOSED tab no longer needs one in force (decision #37).
ALTER TABLE tab DROP CONSTRAINT ck_tab_closing;
ALTER TABLE tab ADD CONSTRAINT ck_tab_closing CHECK (
    status NOT IN ('CLOSING','CLOSED')
    OR (closing_started_at IS NOT NULL AND closing_started_by IS NOT NULL
        AND service_charge_rate IS NOT NULL AND folio_id IS NOT NULL)
);

-- Task qr-table-ordering - the customer orders from the QR code of the table.
--
-- ordering_token is the code printed on the QR of the dining table (decision #18),
-- distinct from tab.public_token, which is born with each tab. Random (version 4),
-- never time-ordered, so a printed code reveals nothing about its neighbours.
-- Regenerating it invalidates the printed QR (decision #15).
ALTER TABLE dining_table ADD COLUMN ordering_token UUID;
UPDATE dining_table SET ordering_token = gen_random_uuid();
ALTER TABLE dining_table ALTER COLUMN ordering_token SET NOT NULL;
ALTER TABLE dining_table ADD CONSTRAINT uk_dining_table_ordering_token UNIQUE (ordering_token);

-- The order the customer sends, waiting for a waiter (decisions #6 and #8). Nothing
-- of it reaches the tab, the kitchen or the folio until it is CONFIRMED.
-- resolved_by is null for EXPIRED: nobody resolved it, the clock did.
CREATE TABLE table_order_request (
    id              UUID PRIMARY KEY,
    property_id     UUID        NOT NULL REFERENCES property(id),
    dining_table_id UUID        NOT NULL REFERENCES dining_table(id),
    status          VARCHAR(20) NOT NULL
                    CHECK (status IN ('PENDING','CONFIRMED','REJECTED','EXPIRED')),
    requested_at    TIMESTAMPTZ NOT NULL,
    expires_at      TIMESTAMPTZ NOT NULL,
    resolved_at     TIMESTAMPTZ,
    resolved_by     UUID,
    tab_id          UUID        REFERENCES tab(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      UUID,
    updated_at      TIMESTAMPTZ,
    updated_by      UUID,
    CONSTRAINT ck_table_order_request_window CHECK (expires_at > requested_at),
    CONSTRAINT ck_table_order_request_resolution CHECK (
        (status = 'PENDING'   AND resolved_at IS NULL AND resolved_by IS NULL AND tab_id IS NULL)
     OR (status = 'CONFIRMED' AND resolved_at IS NOT NULL AND resolved_by IS NOT NULL AND tab_id IS NOT NULL)
     OR (status = 'REJECTED'  AND resolved_at IS NOT NULL AND resolved_by IS NOT NULL AND tab_id IS NULL)
     OR (status = 'EXPIRED'   AND resolved_at IS NOT NULL AND resolved_by IS NULL AND tab_id IS NULL)
    )
);

-- At most one PENDING request per table (decisions #15 and #22). The submission
-- inserts and lets this index answer, so a race between two phones is the same
-- path as a second order an hour later.
CREATE UNIQUE INDEX idx_table_order_request_pending_by_table
    ON table_order_request (dining_table_id)
    WHERE status = 'PENDING';

-- The floor's queue and the expiry job: pending ones, oldest first.
CREATE INDEX idx_table_order_request_pending
    ON table_order_request (property_id, requested_at)
    WHERE status = 'PENDING';

-- One line per item, as the waiter would order it (decision #19). Only what the
-- customer chose is kept; names and prices are read from the menu at confirmation,
-- when Tab.addItem freezes them. Quantity is INTEGER and the instructions TEXT on
-- purpose: they are validated at confirmation (decision #31), so no value sent by
-- a phone may fail the INSERT.
CREATE TABLE table_order_request_line (
    id                     UUID PRIMARY KEY,
    table_order_request_id UUID     NOT NULL REFERENCES table_order_request(id),
    position               SMALLINT NOT NULL,
    menu_item_id           UUID     NOT NULL REFERENCES menu_item(id),
    menu_item_variant_id   UUID,
    quantity               INTEGER,
    special_instructions   TEXT,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by             UUID,
    updated_at             TIMESTAMPTZ,
    updated_by             UUID,
    CONSTRAINT uk_table_order_request_line_position UNIQUE (table_order_request_id, position)
);

-- The modifiers of a line, keyed by position and not by modifier: a repeated
-- modifier is refused at confirmation with the code of Tab.addItem (decision #28).
-- No foreign key to modifier, so an id that does not exist is stored as sent and
-- answers MODIFIER_NOT_FOUND at confirmation instead of a 500 at submission.
CREATE TABLE table_order_request_line_modifier (
    table_order_request_line_id UUID     NOT NULL
                                REFERENCES table_order_request_line(id) ON DELETE CASCADE,
    position                    SMALLINT NOT NULL,
    modifier_id                 UUID     NOT NULL,
    quantity                    INTEGER  NOT NULL,
    PRIMARY KEY (table_order_request_line_id, position)
);

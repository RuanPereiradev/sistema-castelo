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

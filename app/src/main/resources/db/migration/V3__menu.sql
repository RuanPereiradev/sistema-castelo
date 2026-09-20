-- Task 0.8 - menu. The six tables docs/MIGRATIONS.md reserves for V3.
-- menu_item_variant, modifier and menu_item_modifier are created here but only
-- mapped by task 1.2, which owns those aggregates (decision #4).

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
    menu_item_id    UUID          NOT NULL REFERENCES menu_item(id) ON DELETE CASCADE,
    name            VARCHAR(50)   NOT NULL,
    unit_price      NUMERIC(12,2) NOT NULL CHECK (unit_price > 0),
    display_order   SMALLINT      NOT NULL DEFAULT 0,
    is_active       BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by      UUID,
    updated_at      TIMESTAMPTZ,
    updated_by      UUID,
    CONSTRAINT uk_variant_name UNIQUE (menu_item_id, name)
);

CREATE TABLE modifier (
    id              UUID PRIMARY KEY,
    property_id     UUID          NOT NULL REFERENCES property(id),
    name            VARCHAR(100)  NOT NULL,
    price           NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (price >= 0),
    is_active       BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
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

-- day_of_week null means every day. ISO-8601: 1 = Monday.
-- end_time before start_time is a window crossing midnight (22:00 to 02:00).
CREATE TABLE availability_window (
    id              UUID        PRIMARY KEY,
    menu_item_id    UUID        NOT NULL REFERENCES menu_item(id) ON DELETE CASCADE,
    day_of_week     SMALLINT    CHECK (day_of_week BETWEEN 1 AND 7),
    start_time      TIME        NOT NULL,
    end_time        TIME        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      UUID,
    updated_at      TIMESTAMPTZ,
    updated_by      UUID
);

CREATE INDEX idx_menu_item_category ON menu_item (property_id, menu_category_id)
    WHERE is_active;
CREATE INDEX idx_availability_window_item ON availability_window (menu_item_id);

-- Task 3.2 - tab closing: service charge, split bill and the link to the folio.
-- destination accepts ROOM_ACCOUNT already; only task 3.3 writes it.
-- A tab posts a single TabCharge (decision F6); split groups are only the pre-bill.

ALTER TABLE tab
    ADD COLUMN folio_id               UUID         REFERENCES folio(id),
    ADD COLUMN tab_charge_id          UUID         REFERENCES charge(id),
    ADD COLUMN destination            VARCHAR(20)
                                      CHECK (destination IN ('DIRECT_PAYMENT','ROOM_ACCOUNT')),
    ADD COLUMN service_charge_applied BOOLEAN      NOT NULL DEFAULT TRUE,
    ADD COLUMN service_charge_rate    NUMERIC(5,4) CHECK (service_charge_rate >= 0),
    ADD COLUMN guest_count            SMALLINT     CHECK (guest_count BETWEEN 1 AND 999),
    ADD COLUMN closing_started_at     TIMESTAMPTZ,
    ADD COLUMN closing_started_by     UUID,
    ADD COLUMN closed_at              TIMESTAMPTZ,
    ADD COLUMN closed_by              UUID;

-- self-service tabs never carried the charge
UPDATE tab SET service_charge_applied = (origin = 'TABLE_SERVICE');

ALTER TABLE tab
    ADD CONSTRAINT ck_tab_closing CHECK (
        status NOT IN ('CLOSING','CLOSED')
        OR (closing_started_at IS NOT NULL AND closing_started_by IS NOT NULL
            AND service_charge_rate IS NOT NULL AND folio_id IS NOT NULL
            AND tab_charge_id IS NOT NULL)
    ),
    ADD CONSTRAINT ck_tab_closed CHECK (
        status <> 'CLOSED'
        OR (closed_at IS NOT NULL AND closed_by IS NOT NULL AND destination IS NOT NULL)
    );

ALTER TABLE tab_item
    ADD COLUMN split_group           SMALLINT NOT NULL DEFAULT 1 CHECK (split_group BETWEEN 1 AND 99),
    ADD COLUMN service_charge_waived BOOLEAN  NOT NULL DEFAULT FALSE,
    ADD CONSTRAINT ck_tab_item_service_charge_waived
        CHECK (NOT service_charge_waived OR service_chargeable);

CREATE INDEX idx_tab_folio ON tab (folio_id) WHERE folio_id IS NOT NULL;

-- Properties created after this migration get the key from their own seeder (decision G3).
INSERT INTO setting (id, property_id, setting_key, setting_value, value_type, description)
SELECT gen_random_uuid(), p.id, 'restaurant.service_charge_percent', '10.00', 'DECIMAL',
       'Service charge on table-service tabs, in percent points'
  FROM property p
ON CONFLICT (property_id, setting_key) DO NOTHING;

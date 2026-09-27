-- Task 3.5 - kitchen display.
--
-- A READY item stays on the kitchen display until it is delivered (decision K5), so the queue
-- index covers READY too. The two checks are the second line of defence of the moment each
-- status records. The delay limits of each station (decision K11) live in setting, seeded for
-- every existing property; a property created later gets them from whoever creates it.

DROP INDEX idx_kds_queue;
CREATE INDEX idx_kds_queue
    ON tab_item (prep_station, status, ordered_at)
    WHERE status IN ('PENDING','IN_PREPARATION','READY');

ALTER TABLE tab_item
    ADD CONSTRAINT ck_tab_item_ready     CHECK (status <> 'READY'     OR ready_at     IS NOT NULL),
    ADD CONSTRAINT ck_tab_item_delivered CHECK (status <> 'DELIVERED' OR delivered_at IS NOT NULL);

INSERT INTO setting (id, property_id, setting_key, setting_value, value_type, description)
SELECT gen_random_uuid(), property.id, limits.setting_key, limits.setting_value, 'INTEGER', limits.description
  FROM property
 CROSS JOIN (VALUES
        ('restaurant.kitchen-display.kitchen.warning-minutes', '15',
         'Minutes after the order when a KITCHEN ticket calls for attention'),
        ('restaurant.kitchen-display.kitchen.late-minutes',    '25',
         'Minutes after the order when a KITCHEN ticket is late'),
        ('restaurant.kitchen-display.pizza.warning-minutes',   '20',
         'Minutes after the order when a PIZZA ticket calls for attention'),
        ('restaurant.kitchen-display.pizza.late-minutes',      '30',
         'Minutes after the order when a PIZZA ticket is late'),
        ('restaurant.kitchen-display.bar.warning-minutes',     '5',
         'Minutes after the order when a BAR ticket calls for attention'),
        ('restaurant.kitchen-display.bar.late-minutes',        '10',
         'Minutes after the order when a BAR ticket is late')
       ) AS limits (setting_key, setting_value, description)
    ON CONFLICT (property_id, setting_key) DO NOTHING;

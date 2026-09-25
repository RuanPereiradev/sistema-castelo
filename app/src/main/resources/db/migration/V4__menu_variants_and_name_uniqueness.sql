-- Task 1.2 - menu variants and modifiers.

-- Decision #4: a variant can run out on its own (the large pizza is gone, the
-- medium is still served), independently of being taken off the menu (is_active).
ALTER TABLE menu_item_variant
    ADD COLUMN is_available BOOLEAN NOT NULL DEFAULT TRUE;

-- Decision #27: names on the menu are unique ignoring case ("Grande" and
-- "GRANDE" are the same variant). The name is stored as typed, because it is
-- what the public menu shows; only the comparison ignores case. The unique
-- indexes keep the names of the V3 constraints they replace.
ALTER TABLE menu_category DROP CONSTRAINT uk_menu_category_name;
CREATE UNIQUE INDEX uk_menu_category_name ON menu_category (property_id, lower(name));

ALTER TABLE menu_item DROP CONSTRAINT uk_menu_item_name;
CREATE UNIQUE INDEX uk_menu_item_name ON menu_item (property_id, lower(name));

ALTER TABLE menu_item_variant DROP CONSTRAINT uk_variant_name;
CREATE UNIQUE INDEX uk_variant_name ON menu_item_variant (menu_item_id, lower(name));

ALTER TABLE modifier DROP CONSTRAINT uk_modifier_name;
CREATE UNIQUE INDEX uk_modifier_name ON modifier (property_id, lower(name));

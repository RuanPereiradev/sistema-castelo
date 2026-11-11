-- Task no-preparation-items - item of the menu that needs no preparation.
--
-- menu_item.requires_preparation is what the administration checks or clears (decision #2). Every
-- item that already exists keeps going to the kitchen, hence the default.
--
-- tab_item.requires_preparation freezes it at the moment of the order, like the name, the price and
-- the station (decision #4): an item born READY because it needs no preparation stays off the
-- kitchen display, while an item the kitchen marked READY stays on it until delivered (K5), and
-- clearing the box on the menu later does not change what was already ordered. Every item already
-- ordered went through the kitchen, hence the same default.

ALTER TABLE menu_item
    ADD COLUMN requires_preparation BOOLEAN NOT NULL DEFAULT true;

ALTER TABLE tab_item
    ADD COLUMN requires_preparation BOOLEAN NOT NULL DEFAULT true;

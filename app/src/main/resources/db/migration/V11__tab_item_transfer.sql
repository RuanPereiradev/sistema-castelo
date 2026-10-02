-- Task 3.6 - the trail of every item moved between tabs.
--
-- Append-only: one row per movement, never updated, never deleted, not even when
-- the tab is cancelled. It answers "why did table 4 close with less" after any
-- number of hops (decision T11), which tab_item.transferred_from_tab_id cannot:
-- that column is the shortcut to the LAST hop, read by the screen and by the
-- pre-bill without a join, and it is overwritten on every move.
--
-- kind says which operation moved the item (decision T15). Without it a table
-- move of eight items would be indistinguishable from eight separate transfers.

CREATE TABLE tab_item_transfer (
    id             UUID PRIMARY KEY,
    tab_item_id    UUID        NOT NULL REFERENCES tab_item(id),
    from_tab_id    UUID        NOT NULL REFERENCES tab(id),
    to_tab_id      UUID        NOT NULL REFERENCES tab(id),
    kind           VARCHAR(20) NOT NULL
                   CHECK (kind IN ('TRANSFER','MERGE','MOVE')),
    transferred_by UUID        NOT NULL,
    transferred_at TIMESTAMPTZ NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by     UUID,
    updated_at     TIMESTAMPTZ,
    updated_by     UUID,
    CONSTRAINT ck_tab_item_transfer_distinct CHECK (from_tab_id <> to_tab_id)
);

-- The trail of one item, in the order it happened: the multi-hop question.
CREATE INDEX idx_tab_item_transfer_by_item ON tab_item_transfer (tab_item_id, transferred_at);

-- What left a tab, and what arrived on it.
CREATE INDEX idx_tab_item_transfer_from ON tab_item_transfer (from_tab_id);
CREATE INDEX idx_tab_item_transfer_to   ON tab_item_transfer (to_tab_id);

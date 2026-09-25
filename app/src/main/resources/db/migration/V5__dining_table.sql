-- Task 1.5 - dining tables, where the tab of task 2.2 opens.

-- Decision #5: the label ("Mesa 5", "V3") is unique in the property ignoring
-- case, stored as typed. Same rule as decision #27 of task 1.2, so the unique
-- constraint of the schema design becomes an index over lower(label).
-- Decision #6: a dining table is never deleted, only deactivated.
CREATE TABLE dining_table (
    id              UUID PRIMARY KEY,
    property_id     UUID        NOT NULL REFERENCES property(id),
    label           VARCHAR(20) NOT NULL,
    seats           SMALLINT    CHECK (seats BETWEEN 1 AND 999),
    area            VARCHAR(50),
    is_active       BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      UUID,
    updated_at      TIMESTAMPTZ,
    updated_by      UUID
);

CREATE UNIQUE INDEX uk_dining_table_label ON dining_table (property_id, lower(label));

-- Task folio-adjustment-idempotency - the adjustment of a folio takes an idempotency key.
--
-- The same rule as the payment (decision #5 of task 1.3): a retry with the same key
-- and the same body answers the adjustment already written. The key is unique across
-- every folio, in a space of its own: uk_payment_idempotency and
-- uk_cash_movement_idempotency do not see it. Only an adjustment carries a key.
--
-- The adjustments written before this migration have none, so the check that an
-- adjustment carries one is NOT VALID: Postgres applies it to every new or changed
-- row and leaves the old ones alone.
ALTER TABLE charge ADD COLUMN idempotency_key VARCHAR(100);

ALTER TABLE charge
    ADD CONSTRAINT uk_charge_idempotency UNIQUE (idempotency_key);

ALTER TABLE charge
    ADD CONSTRAINT ck_charge_idempotency_adjustment CHECK (
        charge_type = 'ADJUSTMENT' OR idempotency_key IS NULL
    );

ALTER TABLE charge
    ADD CONSTRAINT ck_charge_adjustment_idempotency CHECK (
        charge_type <> 'ADJUSTMENT' OR idempotency_key IS NOT NULL
    ) NOT VALID;

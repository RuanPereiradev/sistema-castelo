-- Task folio-adjustment-idempotency - the adjustment of a folio takes an idempotency key.
--
-- The same rule as the payment (decision #5 of task 1.3): a retry with the same key
-- and the same body answers the adjustment already written. The key is unique across
-- every folio, in a space of its own: uk_payment_idempotency and
-- uk_cash_movement_idempotency do not see it. Only an adjustment carries a key.
--
-- The adjustments written before this migration get a synthetic key, 'legacy-' plus
-- their own id, so the check that an adjustment carries a key is validated over every
-- row: a NOT VALID check would refuse any later UPDATE of an old adjustment, including
-- a backfill in a future migration. No client ever sent these keys, so no retry can
-- match them.
ALTER TABLE charge ADD COLUMN idempotency_key VARCHAR(100);

UPDATE charge
   SET idempotency_key = 'legacy-' || id
 WHERE charge_type = 'ADJUSTMENT';

ALTER TABLE charge
    ADD CONSTRAINT uk_charge_idempotency UNIQUE (idempotency_key);

ALTER TABLE charge
    ADD CONSTRAINT ck_charge_idempotency_adjustment CHECK (
        charge_type = 'ADJUSTMENT' OR idempotency_key IS NULL
    );

ALTER TABLE charge
    ADD CONSTRAINT ck_charge_adjustment_idempotency CHECK (
        charge_type <> 'ADJUSTMENT' OR idempotency_key IS NOT NULL
    );

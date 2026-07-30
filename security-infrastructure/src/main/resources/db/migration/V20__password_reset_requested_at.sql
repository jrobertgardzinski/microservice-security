-- A password reset had NO time column at all, so a link only ever died by being used: one e-mailed
-- months ago still worked. Matched by address alone, such a link outlives the account it was issued
-- for — after the account is closed and the address registered by someone else, redeeming it sets
-- the NEW owner's password. requested_at carries the age the use case now refuses past its TTL.
-- Rows written before this migration are backfilled to the epoch, so every link issued under the old
-- rule is expired on arrival — the safe direction.
ALTER TABLE password_resets ADD COLUMN requested_at TIMESTAMP;
UPDATE password_resets SET requested_at = TIMESTAMP '1970-01-01 00:00:00' WHERE requested_at IS NULL;
ALTER TABLE password_resets ALTER COLUMN requested_at SET NOT NULL;

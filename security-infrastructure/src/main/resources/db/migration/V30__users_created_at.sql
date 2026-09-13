-- DB-14, the other half: an account NOBODY ever verified lived for ever.
--
-- The pending verification row is swept after 30 days (V28 + AbandonedVerificationReaper), so what
-- was left behind was the account itself: an e-mail address and a password hash for somebody who
-- never confirmed the address is theirs, kept with no purpose left to serve, and holding that
-- address against anyone else who might register it.
--
-- Deleting them needs a date, and the users table had none: `created_at` is when the account was
-- opened, which is the only honest clock for "how long has this been unconfirmed".
ALTER TABLE users ADD COLUMN created_at TIMESTAMP;

-- Existing rows are backfilled to NOW, not to the past. The safe direction: an account that has
-- been unverified for a year gets its full window starting today rather than being deleted by the
-- first sweep after this migration. Nobody loses an account to a change of schema.
UPDATE users SET created_at = NOW() WHERE created_at IS NULL;
ALTER TABLE users ALTER COLUMN created_at SET NOT NULL;

-- The sweep reads "unverified AND older than the cutoff", and the verified flag lives in another
-- table, so this index carries the half that is here.
CREATE INDEX IF NOT EXISTS idx_users_created_at ON users (created_at);

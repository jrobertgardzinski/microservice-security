-- Content now lives in two services (memes, comments); the deletion saga completes only when BOTH
-- confirmed their purge.
ALTER TABLE account_deletion_sagas ADD COLUMN memes_purged    BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE account_deletion_sagas ADD COLUMN comments_purged BOOLEAN NOT NULL DEFAULT FALSE;

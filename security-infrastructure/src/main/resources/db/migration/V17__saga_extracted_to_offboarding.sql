-- The account-deletion saga's ORCHESTRATION moved out to the portal (microservice-offboarding):
-- identity no longer tracks per-participant confirmations — it announces the deletion fact and
-- waits for the portal's single outcome. The per-participant columns (V7, V15) retire; the saga
-- row keeps only where the deletion stands (STARTED / COMPLETED / COMPENSATED).
ALTER TABLE account_deletion_sagas DROP COLUMN memes_purged;
ALTER TABLE account_deletion_sagas DROP COLUMN comments_purged;
ALTER TABLE account_deletion_sagas DROP COLUMN collections_purged;

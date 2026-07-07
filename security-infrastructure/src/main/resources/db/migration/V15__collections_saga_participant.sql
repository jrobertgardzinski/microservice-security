-- A third content service joins the deletion saga: microservice-user-collections (the user's
-- favourites/saved refs). The saga now completes only when all THREE have confirmed their purge.
ALTER TABLE account_deletion_sagas ADD COLUMN collections_purged BOOLEAN NOT NULL DEFAULT FALSE;

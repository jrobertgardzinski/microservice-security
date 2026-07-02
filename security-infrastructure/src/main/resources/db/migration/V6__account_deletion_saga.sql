-- Account deletion is a saga: the account locks (pending_deletion) while microservice-memes
-- purges the user's content; only its confirmation deletes the user for good, and a missed
-- confirmation rolls the lock back. The saga row tracks where each deletion stands.
ALTER TABLE users ADD COLUMN pending_deletion BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE account_deletion_sagas (
    id         UUID PRIMARY KEY,
    email      VARCHAR(255) NOT NULL,
    state      VARCHAR(20)  NOT NULL,
    created_at TIMESTAMP    NOT NULL,
    updated_at TIMESTAMP    NOT NULL
);
CREATE INDEX idx_deletion_sagas_started ON account_deletion_sagas (created_at) WHERE state = 'STARTED';

-- The whole schema, in one file: nothing is deployed anywhere, so there is no history to replay.
-- A change edits this file; a running dev database is recreated (docker compose -p security down -v).

CREATE TABLE users (
    id               UUID PRIMARY KEY,                 -- the identity; the access token's subject
    email            VARCHAR(255) NOT NULL UNIQUE,
    normalized_email VARCHAR(255) NOT NULL UNIQUE,     -- lower-cased local part: the identity an address is found by
    password_hash    TEXT         NOT NULL,
    pending_deletion BOOLEAN      NOT NULL DEFAULT FALSE,
    roles            VARCHAR(255) NOT NULL DEFAULT 'USER',
    created_at       TIMESTAMP    NOT NULL
);
CREATE INDEX idx_users_created_at ON users (created_at);

CREATE TABLE authentication_blocks (
    ip_address  VARCHAR(64) PRIMARY KEY,
    expiry_date TIMESTAMP   NOT NULL
);
CREATE INDEX idx_authentication_blocks_expiry ON authentication_blocks (expiry_date);

CREATE TABLE rejected_authentications (
    id                  BIGSERIAL PRIMARY KEY,
    ip_address          VARCHAR(64)  NOT NULL,
    occurred_at         TIMESTAMP    NOT NULL,
    user_agent          VARCHAR(400) NOT NULL DEFAULT '',
    account_fingerprint VARCHAR(64)  NOT NULL DEFAULT ''   -- the lockout counts per (source, attempted account)
);
CREATE INDEX idx_rejected_ip_time ON rejected_authentications (ip_address, occurred_at);
CREATE INDEX idx_rejected_occurred_at ON rejected_authentications (occurred_at);
CREATE INDEX idx_rejected_pair_time ON rejected_authentications (ip_address, account_fingerprint, occurred_at);

CREATE TABLE sessions (
    refresh_token_hash       VARCHAR(64)  PRIMARY KEY,
    email                    VARCHAR(255) NOT NULL,
    refresh_token_expiration TIMESTAMP    NOT NULL,
    access_token_hash        VARCHAR(64)  NOT NULL UNIQUE,
    access_token_expiration  TIMESTAMP    NOT NULL,
    family_id                UUID         NOT NULL,
    status                   VARCHAR(16)  NOT NULL,
    family_started_at        TIMESTAMP    NOT NULL
);
CREATE INDEX idx_sessions_family ON sessions (family_id);
CREATE INDEX idx_sessions_email ON sessions (email);
CREATE INDEX idx_sessions_refresh_expiration ON sessions (refresh_token_expiration);

CREATE TABLE email_verifications (
    email              VARCHAR(255) PRIMARY KEY,
    pending_token_hash VARCHAR(64),
    verified           BOOLEAN      NOT NULL DEFAULT FALSE,
    requested_at       TIMESTAMP    NOT NULL
);
CREATE INDEX idx_email_verifications_token ON email_verifications (pending_token_hash);
CREATE INDEX idx_email_verifications_requested_at ON email_verifications (requested_at) WHERE verified = FALSE;

CREATE TABLE password_resets (
    email        VARCHAR(255) PRIMARY KEY,
    token_hash   VARCHAR(64)  NOT NULL,
    requested_at TIMESTAMP    NOT NULL
);
CREATE INDEX idx_password_resets_token ON password_resets (token_hash);
CREATE INDEX idx_password_resets_requested_at ON password_resets (requested_at);

CREATE TABLE email_changes (
    token_hash    VARCHAR(64)  PRIMARY KEY,
    current_email VARCHAR(255) NOT NULL,
    new_email     VARCHAR(255) NOT NULL,
    started_at    TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX idx_email_changes_started_at ON email_changes (started_at);

-- the transactional outbox: every fact security tells the world leaves through here
CREATE TABLE outbox_events (
    id           UUID PRIMARY KEY,
    topic        VARCHAR(100) NOT NULL,
    event_key    VARCHAR(255) NOT NULL,
    payload      TEXT         NOT NULL,
    created_at   TIMESTAMP    NOT NULL,
    published_at TIMESTAMP,
    failed_at    TIMESTAMP,                              -- given up after the retry budget
    cid          VARCHAR(64),
    traceparent  VARCHAR(64)
);
CREATE INDEX idx_outbox_unpublished ON outbox_events (created_at) WHERE published_at IS NULL AND failed_at IS NULL;
CREATE INDEX idx_outbox_published_at ON outbox_events (published_at) WHERE published_at IS NOT NULL;
CREATE INDEX idx_outbox_failed_at ON outbox_events (failed_at) WHERE failed_at IS NOT NULL;

-- security's own view of an account closure; the participants' progress lives in offboarding
CREATE TABLE account_deletion_sagas (
    id         UUID PRIMARY KEY,
    email      VARCHAR(255) NOT NULL,
    state      VARCHAR(20)  NOT NULL,
    created_at TIMESTAMP    NOT NULL,
    updated_at TIMESTAMP    NOT NULL
);
CREATE INDEX idx_deletion_sagas_started ON account_deletion_sagas (created_at) WHERE state = 'STARTED';
CREATE INDEX idx_deletion_sagas_settled ON account_deletion_sagas (updated_at) WHERE state <> 'STARTED';
CREATE UNIQUE INDEX uq_deletion_sagas_running_email ON account_deletion_sagas (email) WHERE state = 'STARTED';

-- the outcomes offboarding announced and security already acted on (at-least-once delivery)
create table processed_offboarding_outcomes (
    id           varchar(36) primary key,   -- the outcome's id, as offboarding derived it
    outcome_type varchar(64) not null,
    processed_at timestamp   not null
);
create index idx_processed_offboarding_outcomes_at on processed_offboarding_outcomes (processed_at);

CREATE TABLE federated_identities (
    provider_subject VARCHAR(320) PRIMARY KEY,
    provider         VARCHAR(32)  NOT NULL,
    subject          VARCHAR(255) NOT NULL,
    user_email       VARCHAR(255) NOT NULL,
    linked_at        TIMESTAMP    NOT NULL
);
CREATE INDEX idx_federated_user ON federated_identities (user_email);

CREATE TABLE enrolled_factors (
    id              VARCHAR(320) PRIMARY KEY,
    user_email      VARCHAR(255) NOT NULL,
    type            VARCHAR(32)  NOT NULL,
    label           VARCHAR(64)  NOT NULL,
    factor_order    INT          NOT NULL,
    secret_material VARCHAR(512) NOT NULL
);
CREATE INDEX idx_enrolled_factors_user ON enrolled_factors (user_email, factor_order);

CREATE TABLE passwordless_accounts (
    user_email VARCHAR(255) PRIMARY KEY
);

CREATE TABLE recovery_codes (
    id         VARCHAR(600) PRIMARY KEY,
    user_email VARCHAR(255) NOT NULL,
    code_hash  VARCHAR(64)  NOT NULL,
    used       BOOLEAN      NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_recovery_codes_user ON recovery_codes (user_email);

-- the live rung of the configuration ladder: what an administrator changed at runtime
CREATE TABLE security_settings (
    name       VARCHAR(255) PRIMARY KEY,
    value      TEXT         NOT NULL,
    updated_at TIMESTAMP    NOT NULL DEFAULT now()
);

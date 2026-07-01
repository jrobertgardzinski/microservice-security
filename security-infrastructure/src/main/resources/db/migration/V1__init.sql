CREATE TABLE users (
    id               UUID PRIMARY KEY,
    email            VARCHAR(255) NOT NULL UNIQUE,
    normalized_email VARCHAR(255) NOT NULL UNIQUE,
    password_hash    TEXT         NOT NULL
);

CREATE TABLE authentication_blocks (
    ip_address  VARCHAR(64) PRIMARY KEY,
    expiry_date TIMESTAMP NOT NULL
);

CREATE TABLE rejected_authentications (
    id          BIGSERIAL PRIMARY KEY,
    ip_address  VARCHAR(64) NOT NULL,
    occurred_at TIMESTAMP   NOT NULL
);
CREATE INDEX idx_rejected_ip_time ON rejected_authentications (ip_address, occurred_at);

-- sessions are keyed by a SHA-256 hash of the refresh token (64 hex chars); raw tokens are never
-- stored. The access token's hash is kept too (to authorize a presented access token), along with
-- the lineage (family_id) and status; rotated rows are kept so a replayed refresh token is detected.
CREATE TABLE sessions (
    refresh_token_hash       VARCHAR(64) PRIMARY KEY,
    email                    VARCHAR(255) NOT NULL,
    refresh_token_expiration TIMESTAMP    NOT NULL,
    access_token_hash        VARCHAR(64)  NOT NULL UNIQUE,
    access_token_expiration  TIMESTAMP    NOT NULL,
    family_id                UUID         NOT NULL,
    status                   VARCHAR(16)  NOT NULL
);
CREATE INDEX idx_sessions_family ON sessions (family_id);

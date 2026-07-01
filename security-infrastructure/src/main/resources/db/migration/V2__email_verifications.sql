-- Pending e-mail verifications and their outcome, keyed by e-mail. The pending token is stored as a
-- SHA-256 hash (64 hex chars) and cleared once used; raw tokens are never stored.
CREATE TABLE email_verifications (
    email              VARCHAR(255) PRIMARY KEY,
    pending_token_hash VARCHAR(64),
    verified           BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_email_verifications_token ON email_verifications (pending_token_hash);

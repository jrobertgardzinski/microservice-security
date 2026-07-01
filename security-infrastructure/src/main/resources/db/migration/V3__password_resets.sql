-- Pending password resets, keyed by e-mail. The pending token is stored as a SHA-256 hash (64 hex
-- chars); the row is deleted when the token is consumed (single-use). Raw tokens are never stored.
CREATE TABLE password_resets (
    email      VARCHAR(255) PRIMARY KEY,
    token_hash VARCHAR(64) NOT NULL
);
CREATE INDEX idx_password_resets_token ON password_resets (token_hash);

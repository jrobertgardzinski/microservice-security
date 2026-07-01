-- Pending email changes, keyed by the SHA-256 hash (64 hex chars) of the token e-mailed to the new
-- address; the row is deleted when the token is consumed (single-use). Raw tokens are never stored.
CREATE TABLE email_changes (
    token_hash    VARCHAR(64) PRIMARY KEY,
    current_email VARCHAR(255) NOT NULL,
    new_email     VARCHAR(255) NOT NULL
);

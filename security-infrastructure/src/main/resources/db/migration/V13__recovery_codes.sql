-- MFA: single-use recovery codes — the break-glass ALTERNATIVE to any chain link (never a link
-- of their own). Only hashes land here; the plain codes exist once, at generation, on the user's
-- screen. id = 'email|hash' flattens the natural key for plain CRUD, the enrolled_factors trick.
-- A spent code stays as a row (used = TRUE) so the UI can say "N of 10 left".
CREATE TABLE recovery_codes (
    id         VARCHAR(600) PRIMARY KEY,
    user_email VARCHAR(255) NOT NULL,
    code_hash  VARCHAR(64)  NOT NULL,
    used       BOOLEAN      NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_recovery_codes_user ON recovery_codes (user_email);

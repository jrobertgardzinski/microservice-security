-- MFA: the factors each user has enrolled (the per-user half of the config; the deployment half is
-- which adapters are enabled). id = 'email|type' flattens the (user, type) natural key for a plain
-- CRUD id. factor_order fixes the chain position; the password / OAuth login is link #1 and is not
-- a row here. secret_material is factor-specific (an e-mail/phone target for the code channels; an
-- encrypted secret for TOTP later).
CREATE TABLE enrolled_factors (
    id              VARCHAR(320) PRIMARY KEY,
    user_email      VARCHAR(255) NOT NULL,
    type            VARCHAR(32)  NOT NULL,
    label           VARCHAR(64)  NOT NULL,
    factor_order    INT          NOT NULL,
    secret_material VARCHAR(512) NOT NULL
);
CREATE INDEX idx_enrolled_factors_user ON enrolled_factors (user_email, factor_order);

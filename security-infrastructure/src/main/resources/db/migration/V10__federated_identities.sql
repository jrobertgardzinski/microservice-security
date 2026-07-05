-- Social login: which local account a provider's subject opens. One account, many identities —
-- the password row in users and any number of rows here are equal keys to the same user.
-- provider_subject = '<provider>|<subject>' (the natural key flattened for a simple CRUD id).
CREATE TABLE federated_identities (
    provider_subject VARCHAR(320) PRIMARY KEY,
    provider         VARCHAR(32)  NOT NULL,
    subject          VARCHAR(255) NOT NULL,
    user_email       VARCHAR(255) NOT NULL,
    linked_at        TIMESTAMP    NOT NULL
);
CREATE INDEX idx_federated_user ON federated_identities (user_email);

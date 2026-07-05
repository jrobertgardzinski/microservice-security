-- Accounts with no usable password: born through a federated (OAuth) sign-in, not since given a
-- password. The MFA role floor reads this — a password is the first factor and counts toward the
-- floor, a provider login does not. Setting a password (reset flow) deletes the row.
CREATE TABLE passwordless_accounts (
    user_email VARCHAR(255) PRIMARY KEY
);

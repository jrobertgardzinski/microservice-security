package com.jrobertgardzinski.security.domain.repository;

import com.jrobertgardzinski.email.domain.Email;

/**
 * Which accounts have no usable password — the ones born through a federated (OAuth) sign-in and
 * not since given one. The MFA role floor needs this: a password counts as the first factor, but a
 * provider login does not (a compromised Google account must not, by itself, cover part of an
 * admin's floor). Setting a password (through the reset flow) clears the mark.
 */
public interface PasswordlessAccountRepository {

    boolean isPasswordless(Email email);

    void setPasswordless(Email email, boolean passwordless);

    /**
     * Moves the mark to a new address — keyed by the e-mail, and the e-mail is mutable, so the mark
     * must FOLLOW the account. Left behind, a federated account reads as "has a password" under its
     * new address: a step-up then demands a password the account never had (its hash is unguessable
     * random), which locks the owner out of deleting their own account for good.
     */
    void reassign(Email fromEmail, Email toEmail);

    /**
     * Drops the mark — the teardown when the account is deleted. Left behind, the mark would tell a
     * stranger who later registers the freed address that their account has no password, so a
     * step-up would skip the password check on an account that does have one.
     */
    void purge(Email email);
}

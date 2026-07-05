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
}

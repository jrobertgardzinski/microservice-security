package com.jrobertgardzinski.security.system.registration;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.util.constraint.Outcome;

import java.util.List;

/**
 * The email error codes of a registration attempt — a type deliberately distinct
 * from {@link PasswordErrorCodes}, so the two channels can never be swapped when a
 * {@link RegisterResult.Rejected} is built.
 *
 * The only way to create one is {@link #of}, which takes a typed
 * {@code Outcome<Email>}; the constructor is private, so these codes can be
 * neither produced from a password outcome nor assembled from a raw list.
 */
public final class EmailErrorCodes {

    private final List<String> codes;

    private EmailErrorCodes(List<String> codes) {
        this.codes = codes;
    }

    static EmailErrorCodes of(Outcome<Email> outcome) {
        return new EmailErrorCodes(outcome.errorCodes());
    }

    public List<String> codes() {
        return codes;
    }
}

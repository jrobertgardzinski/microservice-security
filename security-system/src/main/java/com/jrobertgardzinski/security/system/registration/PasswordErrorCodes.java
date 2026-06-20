package com.jrobertgardzinski.security.system.registration;

import com.jrobertgardzinski.password.domain.HashedPassword;
import com.jrobertgardzinski.util.constraint.Outcome;

import java.util.List;

/**
 * The password error codes of a registration attempt — a type deliberately distinct
 * from {@link EmailErrorCodes}, so the two channels can never be swapped when a
 * {@link RegisterResult.Rejected} is built.
 *
 * The only way to create one is {@link #of}, which takes a typed
 * {@code Outcome<HashedPassword>}; the constructor is private, so these codes can be
 * neither produced from an email outcome nor assembled from a raw list.
 */
public final class PasswordErrorCodes {

    private final List<String> codes;

    private PasswordErrorCodes(List<String> codes) {
        this.codes = codes;
    }

    static PasswordErrorCodes of(Outcome<HashedPassword> outcome) {
        return new PasswordErrorCodes(outcome.errorCodes());
    }

    public List<String> codes() {
        return codes;
    }
}

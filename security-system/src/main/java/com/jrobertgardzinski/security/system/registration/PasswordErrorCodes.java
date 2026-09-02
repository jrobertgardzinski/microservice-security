package com.jrobertgardzinski.security.system.registration;

import com.jrobertgardzinski.password.domain.HashedPassword;
import com.jrobertgardzinski.util.constraint.Outcome;

import java.util.List;

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

package com.jrobertgardzinski.security.system.registration;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.util.constraint.Outcome;

import java.util.List;

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

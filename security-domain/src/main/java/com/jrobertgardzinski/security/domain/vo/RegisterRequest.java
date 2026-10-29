package com.jrobertgardzinski.security.domain.vo;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.password.domain.PlaintextPassword;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Supplier;

public class RegisterRequest {
    private final Email email;
    private final PlaintextPassword plaintextPassword;

    public RegisterRequest(Supplier<Email> emailSupplier, Supplier<PlaintextPassword> plaintextPasswordSupplier) {
        List<String> errors = new LinkedList<>();
        Email gotEmail = null;
        try {
            gotEmail = emailSupplier.get();
        } catch (RuntimeException e) {
            errors.add(e.getMessage());
        }
        PlaintextPassword gotPlaintextPassword = null;
        try {
            gotPlaintextPassword = plaintextPasswordSupplier.get();
        } catch (RuntimeException e) {
            errors.add(e.getMessage());
        }

        if (!errors.isEmpty()) {
            throw new RuntimeException(errors.toString());
        }
        else {
            this.email = gotEmail;
            this.plaintextPassword = gotPlaintextPassword;
        }
    }

    public Email getEmail() {
        return email;
    }

    public PlaintextPassword getPlaintextPassword() {
        return plaintextPassword;
    }
}

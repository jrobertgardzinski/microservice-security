package com.jrobertgardzinski.security.domain.validation;

import com.jrobertgardzinski.password.policy.domain.PasswordSpecification;
import com.jrobertgardzinski.security.domain.vo.PlaintextPassword;
import com.jrobertgardzinski.security.domain.vo.PlaintextPassword2;

import java.util.Optional;

class MinLengthSpecification implements PasswordSpecification2 {

    private final int minLength;

    MinLengthSpecification(int minLength) {
        if (minLength < 5) {
            throw new IllegalArgumentException("Length must be at least 5!");
        }
        this.minLength = minLength;
    }

    @Override
    public Optional<String> check(PlaintextPassword2 password) {
        return password.getValue().length() < minLength
                ? Optional.of("must be at least %d characters long".formatted(minLength))
                : Optional.empty();
    }
}

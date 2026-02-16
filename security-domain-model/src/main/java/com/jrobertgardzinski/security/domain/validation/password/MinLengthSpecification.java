package com.jrobertgardzinski.security.domain.validation.password;

import com.jrobertgardzinski.security.domain.vo.PlaintextPassword;

import java.util.Optional;

class MinLengthSpecification implements PasswordSpecification {

    private final int minLength;

    MinLengthSpecification(int minLength) {
        if (minLength < 5) {
            throw new IllegalArgumentException("Length must be at least 5!");
        }
        this.minLength = minLength;
    }

    @Override
    public Optional<String> check(PlaintextPassword password) {
        return password.getValue().length() < minLength
                ? Optional.of("must be at least %d characters long".formatted(minLength))
                : Optional.empty();
    }
}

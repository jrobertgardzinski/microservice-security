package com.jrobertgardzinski.password.specifications;

import com.jrobertgardzinski.password.domain.PasswordSpecification;
import com.jrobertgardzinski.password.domain.PlaintextPassword;

import java.util.Optional;

public class MinLengthSpecification implements PasswordSpecification {

    private final int minLength;

    public MinLengthSpecification(int minLength) {
        if (minLength < 5) {
            throw new IllegalArgumentException("minLength must be at least 5");
        }
        this.minLength = minLength;
    }

    @Override
    public Optional<String> check(PlaintextPassword password) {
        return password.value().length() < minLength
                ? Optional.of("must be at least %d characters long".formatted(minLength))
                : Optional.empty();
    }
}

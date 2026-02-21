package com.jrobertgardzinski.password.specifications;

import com.jrobertgardzinski.password.domain.PasswordSpecification;
import com.jrobertgardzinski.password.domain.PlaintextPassword;

import java.util.Optional;
import java.util.regex.Pattern;

public class ContainsDigitSpecification implements PasswordSpecification {

    private static final Pattern DIGIT = Pattern.compile("\\d");

    @Override
    public Optional<String> check(PlaintextPassword password) {
        return DIGIT.matcher(password.value()).find()
                ? Optional.empty()
                : Optional.of("must contain a digit");
    }
}

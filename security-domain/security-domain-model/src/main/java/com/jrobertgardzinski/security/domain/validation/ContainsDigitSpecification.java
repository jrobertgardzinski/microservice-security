package com.jrobertgardzinski.security.domain.validation;

import com.jrobertgardzinski.security.domain.vo.PlaintextPassword2;

import java.util.Optional;
import java.util.regex.Pattern;

class ContainsDigitSpecification implements PasswordSpecification2 {

    private static final Pattern DIGIT = Pattern.compile("\\d");

    @Override
    public Optional<String> check(PlaintextPassword2 password) {
        return DIGIT.matcher(password.getValue()).find()
                ? Optional.empty()
                : Optional.of("must contain a digit");
    }
}

package com.jrobertgardzinski.security.domain.validation.password;

import com.jrobertgardzinski.security.domain.vo.PlaintextPassword;

import java.util.Optional;
import java.util.regex.Pattern;

class ContainsSpecialCharSpecification implements PasswordSpecification {

    private final String specialChars;
    private final Pattern pattern;

    ContainsSpecialCharSpecification(String specialChars) {
        this.specialChars = specialChars;
        this.pattern = Pattern.compile("[" + Pattern.quote(specialChars) + "]");
    }

    @Override
    public Optional<String> check(PlaintextPassword password) {
        return pattern.matcher(password.getValue()).find()
                ? Optional.empty()
                : Optional.of("must contain one of special characters: [%s]".formatted(specialChars));
    }
}

package com.jrobertgardzinski.security.domain.validation;

import com.jrobertgardzinski.password.policy.domain.PasswordSpecification;
import com.jrobertgardzinski.security.domain.vo.PlaintextPassword;
import com.jrobertgardzinski.security.domain.vo.PlaintextPassword2;

import java.util.Optional;
import java.util.regex.Pattern;

class ContainsSpecialCharSpecification implements PasswordSpecification2 {

    private static final String SPECIAL_CHARS = "#?!";
    private static final Pattern SPECIAL = Pattern.compile("[#?!]");

    @Override
    public Optional<String> check(PlaintextPassword2 password) {
        return SPECIAL.matcher(password.getValue()).find()
                ? Optional.empty()
                : Optional.of("must contain one of special characters: [%s]".formatted(SPECIAL_CHARS));
    }
}

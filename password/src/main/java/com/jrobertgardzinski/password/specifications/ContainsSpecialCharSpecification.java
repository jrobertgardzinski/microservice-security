package com.jrobertgardzinski.password.specifications;

import com.jrobertgardzinski.password.domain.PasswordSpecification;
import com.jrobertgardzinski.password.domain.PlaintextPassword;

import java.util.Optional;
import java.util.regex.Pattern;

public class ContainsSpecialCharSpecification implements PasswordSpecification {

    private final String specialChars;
    private final Pattern pattern;

    public ContainsSpecialCharSpecification(String specialChars) {
        this.specialChars = specialChars;
        this.pattern = Pattern.compile("[" + Pattern.quote(specialChars) + "]");
    }

    @Override
    public Optional<String> check(PlaintextPassword password) {
        return pattern.matcher(password.value()).find()
                ? Optional.empty()
                : Optional.of("must contain one of the special characters: [%s]".formatted(specialChars));
    }
}

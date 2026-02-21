package com.jrobertgardzinski.password.specifications;

import com.jrobertgardzinski.password.domain.PasswordSpecification;
import com.jrobertgardzinski.password.domain.PlaintextPassword;

import java.util.Optional;
import java.util.regex.Pattern;

public class ContainsUppercaseSpecification implements PasswordSpecification {

    private static final Pattern UPPERCASE = Pattern.compile("[A-Z]");

    @Override
    public Optional<String> check(PlaintextPassword password) {
        return UPPERCASE.matcher(password.value()).find()
                ? Optional.empty()
                : Optional.of("must contain an uppercase letter");
    }
}

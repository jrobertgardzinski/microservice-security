package com.jrobertgardzinski.password.specifications;

import com.jrobertgardzinski.password.domain.PasswordSpecification;
import com.jrobertgardzinski.password.domain.PlaintextPassword;

import java.util.Optional;
import java.util.regex.Pattern;

public class ContainsLowercaseSpecification implements PasswordSpecification {

    private static final Pattern LOWERCASE = Pattern.compile("[a-z]");

    @Override
    public Optional<String> check(PlaintextPassword password) {
        return LOWERCASE.matcher(password.value()).find()
                ? Optional.empty()
                : Optional.of("must contain a lowercase letter");
    }
}

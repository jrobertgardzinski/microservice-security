package com.jrobertgardzinski.security.domain.validation;

import com.jrobertgardzinski.security.domain.validation.password.PasswordSpecification;
import com.jrobertgardzinski.security.domain.vo.PlaintextPassword;

import java.util.Optional;

class ContainsUppercaseSpecification implements PasswordSpecification {

    @Override
    public Optional<String> check(PlaintextPassword password) {
        return password.getValue().chars().anyMatch(Character::isUpperCase)
                ? Optional.empty()
                : Optional.of("must contain an uppercase letter");
    }
}

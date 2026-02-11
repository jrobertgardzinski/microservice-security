package com.jrobertgardzinski.security.domain.validation;

import com.jrobertgardzinski.security.domain.vo.PlaintextPassword2;

import java.util.Optional;

class ContainsLowercaseSpecification implements PasswordSpecification2 {

    @Override
    public Optional<String> check(PlaintextPassword2 password) {
        return password.getValue().chars().anyMatch(Character::isLowerCase)
                ? Optional.empty()
                : Optional.of("must contain a lowercase letter");
    }
}

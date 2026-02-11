package com.jrobertgardzinski.security.domain.validation;

import com.jrobertgardzinski.password.policy.domain.PasswordSpecification;
import com.jrobertgardzinski.security.domain.vo.PlaintextPassword;
import com.jrobertgardzinski.security.domain.vo.PlaintextPassword2;

import java.util.Optional;

class ContainsUppercaseSpecification implements PasswordSpecification2 {

    @Override
    public Optional<String> check(PlaintextPassword2 password) {
        return password.getValue().chars().anyMatch(Character::isUpperCase)
                ? Optional.empty()
                : Optional.of("must contain an uppercase letter");
    }
}

package com.jrobertgardzinski.security.domain.validation;

import com.jrobertgardzinski.security.domain.vo.PlaintextPassword2;

import java.util.Optional;

public interface PasswordSpecification2 {
    Optional<String> check(PlaintextPassword2 password);
}

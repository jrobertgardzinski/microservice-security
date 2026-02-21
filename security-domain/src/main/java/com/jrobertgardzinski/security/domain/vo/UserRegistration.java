package com.jrobertgardzinski.security.domain.vo;

import com.jrobertgardzinski.password.domain.PlaintextPassword;

public record UserRegistration(
        Email email,
        PlaintextPassword plaintextPassword
) {
}

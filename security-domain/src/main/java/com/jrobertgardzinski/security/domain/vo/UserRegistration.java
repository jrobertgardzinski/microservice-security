package com.jrobertgardzinski.security.domain.vo;

import com.jrobertgardzinski.password.domain.PlaintextPassword;

/**
 * A prospective user's request to join the system.
 */
public record UserRegistration(
        Email email,
        PlaintextPassword plaintextPassword
) {
}

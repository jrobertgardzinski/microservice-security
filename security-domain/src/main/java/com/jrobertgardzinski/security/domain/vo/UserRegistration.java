package com.jrobertgardzinski.security.domain.vo;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.password.domain.HashedPassword;

/**
 * A prospective user's request to join the system, with the password already hashed.
 */
public record UserRegistration(
        Email email,
        HashedPassword passwordHash
) {
}

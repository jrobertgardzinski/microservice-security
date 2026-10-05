package com.jrobertgardzinski.security.domain.authentication;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.password.domain.PlaintextPassword;

/**
 * Proof of identity presented by a user during authentication.
 */
public record Credentials(
        Email email,
        PlaintextPassword plaintextPassword
) {
}

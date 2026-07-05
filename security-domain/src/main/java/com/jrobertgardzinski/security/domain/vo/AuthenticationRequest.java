package com.jrobertgardzinski.security.domain.vo;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.password.domain.PlaintextPassword;

/**
 * A user's attempt to authenticate and gain access to the system.
 */
public record AuthenticationRequest (
        Source source,
        Email email,
        PlaintextPassword plaintextPassword
) {
}
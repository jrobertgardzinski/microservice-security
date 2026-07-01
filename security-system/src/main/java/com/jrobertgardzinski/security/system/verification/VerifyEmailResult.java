package com.jrobertgardzinski.security.system.verification;

import com.jrobertgardzinski.email.domain.Email;

/**
 * Outcome of {@link VerifyEmail}: the address was verified, or the token was rejected.
 */
public sealed interface VerifyEmailResult {

    record Verified(Email email) implements VerifyEmailResult {}

    record Rejected() implements VerifyEmailResult {}
}

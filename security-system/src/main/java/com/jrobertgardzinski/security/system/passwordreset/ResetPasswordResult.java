package com.jrobertgardzinski.security.system.passwordreset;

import com.jrobertgardzinski.email.domain.Email;

/**
 * Outcome of {@link ResetPassword}: the password was reset, the token was rejected, or the new
 * password did not meet the policy.
 */
public sealed interface ResetPasswordResult {

    record PasswordReset(Email email) implements ResetPasswordResult {}

    record InvalidToken() implements ResetPasswordResult {}

    record WeakPassword() implements ResetPasswordResult {}
}

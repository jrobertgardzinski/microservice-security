package com.jrobertgardzinski.security.system.account;

import com.jrobertgardzinski.email.domain.Email;

/**
 * Outcome of {@link ConfirmEmailChange}: the address was changed, or the token was rejected.
 */
public sealed interface ConfirmEmailChangeResult {

    record EmailChanged(Email newEmail) implements ConfirmEmailChangeResult {}

    record InvalidToken() implements ConfirmEmailChangeResult {}
}

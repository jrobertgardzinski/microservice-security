package com.jrobertgardzinski.security.system.authorization;

import com.jrobertgardzinski.email.domain.Email;

public sealed interface AuthorizationResult {
    /** The access token is valid; the request acts as this user. */
    record Authorized(Email email) implements AuthorizationResult {}

    /** No valid, unexpired session matched the presented access token. */
    record Unauthorized() implements AuthorizationResult {}
}

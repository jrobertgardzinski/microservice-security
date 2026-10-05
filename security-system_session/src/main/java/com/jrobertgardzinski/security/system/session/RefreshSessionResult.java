package com.jrobertgardzinski.security.system.session;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.entity.SessionTokens;

public sealed interface RefreshSessionResult {
    /** A new session was issued; the old refresh token has been rotated out. */
    record Refreshed(SessionTokens sessionTokens) implements RefreshSessionResult {}

    /** The session was found but its refresh token had expired. */
    record Expired(Email email) implements RefreshSessionResult {}

    /** No session matched the presented refresh token. */
    record NotFound() implements RefreshSessionResult {}

    /** An already-rotated refresh token was replayed — theft. The whole family has been revoked. */
    record ReuseDetected() implements RefreshSessionResult {}
}

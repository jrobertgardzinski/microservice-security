package com.jrobertgardzinski.security.domain.vo;

import java.util.Objects;
import java.util.UUID;

/**
 * Opaque identifier of an in-progress multi-factor authentication session.
 * Server-issued, client carries it between factor calls.
 */
public record AuthSessionId(UUID value) {

    public AuthSessionId {
        Objects.requireNonNull(value);
    }

    public static AuthSessionId generate() {
        return new AuthSessionId(UUID.randomUUID());
    }
}

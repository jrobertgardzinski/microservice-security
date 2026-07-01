package com.jrobertgardzinski.persistence;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

/**
 * Row of the {@code password_resets} table, keyed by e-mail. Holds the SHA-256 hash of the pending
 * reset token; the row is removed when the token is consumed (single-use). Raw tokens are never stored.
 */
@MappedEntity("password_resets")
record PasswordResetEntity(
        @Id String email,
        String tokenHash) {
}

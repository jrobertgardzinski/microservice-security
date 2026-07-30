package com.jrobertgardzinski.persistence;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

/**
 * Row of the {@code password_resets} table, keyed by e-mail. Holds the SHA-256 hash of the pending
 * reset token and WHEN it was requested; the row is removed when the token is consumed (single-use).
 * The timestamp is what makes the link expire — single use alone let a months-old link still work.
 * Raw tokens are never stored.
 */
@MappedEntity("password_resets")
record PasswordResetEntity(
        @Id String email,
        String tokenHash,
        java.time.LocalDateTime requestedAt) {
}

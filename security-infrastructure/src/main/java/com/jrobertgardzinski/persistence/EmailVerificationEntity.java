package com.jrobertgardzinski.persistence;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

/**
 * Row of the {@code email_verifications} table, keyed by e-mail. Holds the SHA-256 hash of the
 * pending verification token (cleared once used) and whether the address has been verified. Raw
 * tokens are never stored.
 */
@MappedEntity("email_verifications")
record EmailVerificationEntity(
        @Id String email,
        String pendingTokenHash,
        boolean verified) {
}

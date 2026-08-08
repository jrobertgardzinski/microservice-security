package com.jrobertgardzinski.persistence;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

/**
 * Row of the {@code email_changes} table, keyed by the SHA-256 hash of the pending token. Holds the
 * current and new addresses; the row is removed when the token is consumed (single-use). Raw tokens
 * are never stored.
 */
@MappedEntity("email_changes")
record EmailChangeEntity(
        @Id String tokenHash,
        String currentEmail,
        String newEmail) {
}

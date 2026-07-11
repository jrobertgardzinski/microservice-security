package com.jrobertgardzinski.persistence;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

import java.time.Instant;
import java.util.UUID;

/**
 * Row of the {@code account_deletion_sagas} table. Per-participant columns are gone (V17) — who
 * confirms what is the portal orchestrator's state, not identity's.
 */
@MappedEntity("account_deletion_sagas")
record AccountDeletionSagaEntity(
        @Id UUID id,
        String email,
        String state,
        Instant createdAt,
        Instant updatedAt) {
}

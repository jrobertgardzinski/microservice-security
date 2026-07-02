package com.jrobertgardzinski.persistence;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

import java.time.Instant;
import java.util.UUID;

/** Row of the {@code account_deletion_sagas} table. */
@MappedEntity("account_deletion_sagas")
record AccountDeletionSagaEntity(
        @Id UUID id,
        String email,
        String state,
        Instant createdAt,
        Instant updatedAt) {
}

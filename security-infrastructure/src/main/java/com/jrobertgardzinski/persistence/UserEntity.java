package com.jrobertgardzinski.persistence;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

import java.util.UUID;

/** Row of the {@code users} table. Field names map to snake_case columns by Micronaut Data default. */
@MappedEntity("users")
record UserEntity(@Id UUID id, String email, String normalizedEmail, String passwordHash,
                  boolean pendingDeletion, String roles) {
}

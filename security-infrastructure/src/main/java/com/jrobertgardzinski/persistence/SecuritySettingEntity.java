package com.jrobertgardzinski.persistence;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

/**
 * Row of the {@code security_settings} table — the runtime rung of the layered configuration
 * ladder. The name is the very key string the other rungs use (application.yml, the hardcoded
 * default); the value is text and each reading adapter parses it for its own type. Absence of a
 * row is a valid state: the rung is vacant and the ladder falls through.
 */
@MappedEntity("security_settings")
record SecuritySettingEntity(
        @Id String name,
        String value,
        java.time.LocalDateTime updatedAt) {
}

package com.jrobertgardzinski.security.custom.password.persistence;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

/**
 * Row of the {@code security_settings} table — the live level of the ladder. The name is the very
 * key string the other levels use (application.yml, the hardcoded default); the value is text and
 * each reading adapter parses it for its own type. No row is a valid state: the level is vacant
 * and the ladder falls through.
 */
@MappedEntity("security_settings")
record SecuritySettingEntity(
        @Id String name,
        String value,
        java.time.LocalDateTime updatedAt) {
}

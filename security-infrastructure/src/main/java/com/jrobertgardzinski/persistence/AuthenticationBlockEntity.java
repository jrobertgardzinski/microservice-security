package com.jrobertgardzinski.persistence;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

import java.time.LocalDateTime;

/** Row of the {@code authentication_blocks} table; one block per source IP. */
@MappedEntity("authentication_blocks")
record AuthenticationBlockEntity(@Id String ipAddress, LocalDateTime expiryDate) {
}

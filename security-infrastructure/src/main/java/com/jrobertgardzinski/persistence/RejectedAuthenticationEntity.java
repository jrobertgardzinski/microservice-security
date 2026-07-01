package com.jrobertgardzinski.persistence;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

import java.time.LocalDateTime;

/** Row of the {@code rejected_authentications} table; id is database-generated. */
@MappedEntity("rejected_authentications")
record RejectedAuthenticationEntity(@Id @GeneratedValue Long id, String ipAddress, LocalDateTime occurredAt) {
}

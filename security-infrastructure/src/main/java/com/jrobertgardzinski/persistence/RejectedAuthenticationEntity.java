package com.jrobertgardzinski.persistence;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

import java.time.LocalDateTime;

/**
 * Row of the {@code rejected_authentications} table; id is database-generated. The user agent is
 * observed context kept for forensics only — the guard counts by IP alone.
 */
@MappedEntity("rejected_authentications")
record RejectedAuthenticationEntity(@Id @GeneratedValue Long id, String ipAddress, String userAgent,
                                    LocalDateTime occurredAt) {
}

package com.jrobertgardzinski.persistence;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Row of the {@code sessions} table, keyed by a hash of the refresh token. Holds the access token's
 * hash (to authorize a presented access token), both expiries, the lineage ({@code familyId}) and a
 * status ({@code ACTIVE}/{@code ROTATED}). Rotated rows are kept for theft detection. Raw tokens are
 * never stored.
 */
@MappedEntity("sessions")
record SessionEntity(
        @Id String refreshTokenHash,
        String email,
        LocalDateTime refreshTokenExpiration,
        String accessTokenHash,
        LocalDateTime accessTokenExpiration,
        UUID familyId,
        String status) {
}

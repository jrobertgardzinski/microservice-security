package com.jrobertgardzinski.security.domain.vo;

import java.util.UUID;

/**
 * Identifies a session lineage: the original session created at authentication and every session it
 * is rotated into on refresh share one family. Reuse detection revokes by family, so a replayed
 * (already-rotated) refresh token takes down the whole lineage, not just one token.
 */
public record SessionFamily(UUID value) {

    public static SessionFamily start() {
        return new SessionFamily(UUID.randomUUID());
    }
}

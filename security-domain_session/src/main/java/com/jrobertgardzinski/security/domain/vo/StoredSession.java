package com.jrobertgardzinski.security.domain.vo;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.vo.token.expiration.RefreshTokenExpiration;

/**
 * The stored essentials of a session, found by its refresh token: who it belongs to, when its
 * refresh token expires, which lineage it belongs to ({@link SessionFamily}), whether it is still
 * active or already rotated, and when that lineage BEGAN. The raw tokens are intentionally absent —
 * a store keeps only a hash of the refresh token, never the token itself.
 *
 * <p>{@code familyStartedAt} is what makes a session's age absolute. Each refresh grants a fresh
 * window, so the row's own expiry only ever says how long this session may sit idle; the lineage's
 * start is the one date a refresh cannot move, and it is inherited by every successor.
 */
public record StoredSession(
        Email email,
        RefreshTokenExpiration refreshTokenExpiration,
        SessionFamily family,
        SessionStatus status,
        java.time.LocalDateTime familyStartedAt) {
}

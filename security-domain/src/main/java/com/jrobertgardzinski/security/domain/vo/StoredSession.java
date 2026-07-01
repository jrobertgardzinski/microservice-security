package com.jrobertgardzinski.security.domain.vo;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.vo.token.expiration.RefreshTokenExpiration;

/**
 * The stored essentials of a session, found by its refresh token: who it belongs to, when its
 * refresh token expires, which lineage it belongs to ({@link SessionFamily}) and whether it is
 * still active or already rotated. The raw tokens are intentionally absent — a store keeps only a
 * hash of the refresh token, never the token itself.
 */
public record StoredSession(
        Email email,
        RefreshTokenExpiration refreshTokenExpiration,
        SessionFamily family,
        SessionStatus status) {
}

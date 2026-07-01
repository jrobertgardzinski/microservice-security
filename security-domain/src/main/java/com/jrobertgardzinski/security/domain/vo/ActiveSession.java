package com.jrobertgardzinski.security.domain.vo;

import com.jrobertgardzinski.security.domain.vo.token.expiration.RefreshTokenExpiration;

/**
 * A user's active session as shown when listing sessions: which lineage it belongs to and when its
 * refresh token expires. No raw tokens are exposed.
 */
public record ActiveSession(SessionFamily family, RefreshTokenExpiration refreshTokenExpiration) {
}

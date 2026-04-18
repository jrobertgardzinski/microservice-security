package com.jrobertgardzinski.security.domain.vo.token.expiration;

import com.jrobertgardzinski.security.domain.vo.token.AccessToken;

/**
 * The point in time at which an {@link AccessToken} expires.
 */
public record AuthorizationTokenExpiration(TokenExpiration value) {
}

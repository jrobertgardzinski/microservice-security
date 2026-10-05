package com.jrobertgardzinski.security.domain.session;


/**
 * A request to renew a session without re-authenticating. The refresh token is the only thing the
 * client presents — the session (and thus the user) is found from it.
 */
public record SessionRefreshRequest(RefreshToken refreshToken) {
}

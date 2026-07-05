package com.jrobertgardzinski.security.domain.port;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.vo.token.AccessToken;
import com.jrobertgardzinski.security.domain.vo.token.expiration.AuthorizationTokenExpiration;

/**
 * Outbound port that mints the access-token VALUE for a new session. The domain does not care what
 * the value looks like — it stores and compares it as an opaque secret either way (which is what
 * keeps logout and revoke-all instant). An adapter may mint a random string, or a signed JWT whose
 * claims other services can verify offline without calling back.
 */
@FunctionalInterface
public interface AccessTokenMint {

    AccessToken mint(Email email, AuthorizationTokenExpiration expiration);

    /** Opaque random values — the minimal mint, right for unit tests and fallbacks. */
    AccessTokenMint RANDOM = (email, expiration) -> AccessToken.random();
}

package com.jrobertgardzinski.security.system.authorization;

import com.jrobertgardzinski.security.domain.repository.SessionRepository;
import com.jrobertgardzinski.security.domain.vo.token.AccessToken;

import java.time.Clock;

/**
 * Authorizes a request by its access token: the token names a session, which must exist and not be
 * expired. An opaque token validated by lookup (not a self-contained JWT), so a session can be
 * revoked — once its row is gone (e.g. rotated away on refresh), the access token stops working.
 */
public class Authorize {

    private final SessionRepository sessionRepository;
    private final Clock clock;

    public Authorize(SessionRepository sessionRepository, Clock clock) {
        this.sessionRepository = sessionRepository;
        this.clock = clock;
    }

    public AuthorizationResult execute(AccessToken accessToken) {
        return sessionRepository.findByAccessToken(accessToken)
                .filter(grant -> !grant.expiration().hasExpired(clock))
                .<AuthorizationResult>map(grant -> new AuthorizationResult.Authorized(grant.email()))
                .orElseGet(AuthorizationResult.Unauthorized::new);
    }
}

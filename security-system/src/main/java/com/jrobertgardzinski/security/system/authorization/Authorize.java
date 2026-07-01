package com.jrobertgardzinski.security.system.authorization;

import com.jrobertgardzinski.security.domain.repository.AuthorizationDataRepository;
import com.jrobertgardzinski.security.domain.vo.token.AccessToken;

import java.time.Clock;

/**
 * Authorizes a request by its access token: the token names a session, which must exist and not be
 * expired. An opaque token validated by lookup (not a self-contained JWT), so a session can be
 * revoked — once its row is gone (e.g. rotated away on refresh), the access token stops working.
 */
public class Authorize {

    private final AuthorizationDataRepository authorizationDataRepository;
    private final Clock clock;

    public Authorize(AuthorizationDataRepository authorizationDataRepository, Clock clock) {
        this.authorizationDataRepository = authorizationDataRepository;
        this.clock = clock;
    }

    public AuthorizationResult execute(AccessToken accessToken) {
        return authorizationDataRepository.findByAccessToken(accessToken)
                .filter(grant -> !grant.expiration().hasExpired(clock))
                .<AuthorizationResult>map(grant -> new AuthorizationResult.Authorized(grant.email()))
                .orElseGet(AuthorizationResult.Unauthorized::new);
    }
}

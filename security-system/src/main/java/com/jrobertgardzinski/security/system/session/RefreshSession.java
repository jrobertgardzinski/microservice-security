package com.jrobertgardzinski.security.system.session;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.entity.SessionTokens;
import com.jrobertgardzinski.security.domain.repository.AuthorizationDataRepository;
import com.jrobertgardzinski.security.domain.vo.SessionRefreshRequest;
import com.jrobertgardzinski.security.domain.vo.SessionTokensConfig;
import com.jrobertgardzinski.security.domain.vo.token.expiration.RefreshTokenExpiration;

import java.time.Clock;
import java.util.Optional;

public class RefreshSession {
    private final AuthorizationDataRepository authorizationDataRepository;
    private final Clock clock;
    private final SessionTokensConfig config;

    public RefreshSession(AuthorizationDataRepository authorizationDataRepository, Clock clock, SessionTokensConfig config) {
        this.authorizationDataRepository = authorizationDataRepository;
        this.clock = clock;
        this.config = config;
    }

    public RefreshSessionResult execute(SessionRefreshRequest request) {
        Email email = request.email();
        Optional<RefreshTokenExpiration> optionalRefreshTokenExpiration = Optional.ofNullable(
                authorizationDataRepository.findRefreshTokenExpirationBy(email, request.refreshToken()));

        return optionalRefreshTokenExpiration
                .<RefreshSessionResult>map(expiration -> {
                    authorizationDataRepository.deleteBy(email);
                    return expiration.hasExpired(clock)
                            ? new RefreshSessionResult.Expired(email)
                            : new RefreshSessionResult.Refreshed(
                                    authorizationDataRepository.create(
                                            SessionTokens.createFor(email, config, clock)));
                })
                .orElseGet(() -> new RefreshSessionResult.NotFound(email));
    }
}

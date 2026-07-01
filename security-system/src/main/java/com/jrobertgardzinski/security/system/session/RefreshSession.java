package com.jrobertgardzinski.security.system.session;

import com.jrobertgardzinski.security.domain.entity.SessionTokens;
import com.jrobertgardzinski.security.domain.repository.AuthorizationDataRepository;
import com.jrobertgardzinski.security.domain.vo.SessionRefreshRequest;
import com.jrobertgardzinski.security.domain.vo.SessionStatus;
import com.jrobertgardzinski.security.domain.vo.SessionTokensConfig;
import com.jrobertgardzinski.security.domain.vo.token.RefreshToken;

import java.time.Clock;

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
        RefreshToken refreshToken = request.refreshToken();

        return authorizationDataRepository.findByRefreshToken(refreshToken)
                .<RefreshSessionResult>map(session -> {
                    if (session.status() == SessionStatus.ROTATED) {
                        // this refresh token was already rotated away — a replay signals theft
                        authorizationDataRepository.revokeFamily(session.family());
                        return new RefreshSessionResult.ReuseDetected();
                    }
                    if (session.refreshTokenExpiration().hasExpired(clock)) {
                        return new RefreshSessionResult.Expired(session.email());
                    }
                    // single-use: rotate the presented token out, issue a new one in the same family
                    authorizationDataRepository.markRotated(refreshToken);
                    return new RefreshSessionResult.Refreshed(
                            authorizationDataRepository.create(
                                    SessionTokens.createFor(session.email(), config, clock), session.family()));
                })
                .orElseGet(RefreshSessionResult.NotFound::new);
    }
}

package com.jrobertgardzinski.security.system.feature;

import com.jrobertgardzinski.security.config.SessionTokensConfig;
import com.jrobertgardzinski.security.domain.entity.SessionTokens;
import com.jrobertgardzinski.security.domain.event.refresh.NoRefreshTokenFoundEvent;
import com.jrobertgardzinski.security.domain.event.refresh.RefreshTokenEvent;
import com.jrobertgardzinski.security.domain.event.refresh.RefreshTokenExpiredEvent;
import com.jrobertgardzinski.security.domain.event.refresh.RefreshTokenPassedEvent;
import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.repository.AuthorizationDataRepository;
import com.jrobertgardzinski.security.domain.vo.SessionRefreshRequest;
import com.jrobertgardzinski.security.domain.vo.token.RefreshToken;
import com.jrobertgardzinski.security.domain.vo.token.expiration.RefreshTokenExpiration;

import java.time.Clock;
import java.util.function.Function;

public class RefreshSession implements Function<SessionRefreshRequest, RefreshTokenEvent> {
    private final AuthorizationDataRepository authorizationDataRepository;
    private final Clock clock;
    private final SessionTokensConfig config;

    public RefreshSession(AuthorizationDataRepository authorizationDataRepository, Clock clock, SessionTokensConfig config) {
        this.authorizationDataRepository = authorizationDataRepository;
        this.clock = clock;
        this.config = config;
    }

    @Override
    public RefreshTokenEvent apply(SessionRefreshRequest sessionRefreshRequest) {
        Email email = sessionRefreshRequest.email();
        RefreshToken refreshToken = sessionRefreshRequest.refreshToken();
        RefreshTokenExpiration refreshTokenExpiration = authorizationDataRepository.findRefreshTokenExpirationBy(email, refreshToken);
        if (refreshTokenExpiration == null) {
            return new NoRefreshTokenFoundEvent(email);
        }
        authorizationDataRepository.deleteBy(email);
        if (refreshTokenExpiration.hasExpired(clock)) {
            return new RefreshTokenExpiredEvent(email);
        }
        return new RefreshTokenPassedEvent(
                authorizationDataRepository.create(
                        SessionTokens.createFor(email, config.refreshTokenValidityHours().value(), config.accessTokenValidityHours().value(), clock)));
    }
}

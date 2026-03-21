package com.jrobertgardzinski.security.system.feature;

import com.jrobertgardzinski.security.domain.entity.SessionTokens;
import com.jrobertgardzinski.security.domain.event.refresh.NoRefreshTokenFoundEvent;
import com.jrobertgardzinski.security.domain.event.refresh.RefreshTokenEvent;
import com.jrobertgardzinski.security.domain.event.refresh.RefreshTokenExpiredEvent;
import com.jrobertgardzinski.security.domain.event.refresh.RefreshTokenPassedEvent;
import com.jrobertgardzinski.security.domain.repository.AuthorizationDataRepository;
import com.jrobertgardzinski.security.domain.vo.Email;
import com.jrobertgardzinski.security.domain.vo.SessionRefreshRequest;
import com.jrobertgardzinski.security.domain.vo.token.RefreshToken;
import com.jrobertgardzinski.security.domain.vo.token.expiration.RefreshTokenExpiration;

import java.time.Clock;
import java.util.function.Function;

public class RefreshSession implements Function<SessionRefreshRequest, RefreshTokenEvent> {
    private final AuthorizationDataRepository authorizationDataRepository;
    private final Clock clock;
    private final int refreshTokenValidityHours;
    private final int accessTokenValidityHours;

    public RefreshSession(AuthorizationDataRepository authorizationDataRepository, Clock clock,
                          int refreshTokenValidityHours, int accessTokenValidityHours) {
        this.authorizationDataRepository = authorizationDataRepository;
        this.clock = clock;
        this.refreshTokenValidityHours = refreshTokenValidityHours;
        this.accessTokenValidityHours = accessTokenValidityHours;
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
                        SessionTokens.createFor(email, refreshTokenValidityHours, accessTokenValidityHours, clock)));
    }
}

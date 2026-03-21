package com.jrobertgardzinski.security.system.feature;

import com.jrobertgardzinski.security.domain.entity.SessionTokens;
import com.jrobertgardzinski.security.domain.event.authentication.AuthenticationPassedEvent;
import com.jrobertgardzinski.security.domain.repository.AuthorizationDataRepository;

import java.time.Clock;
import java.util.function.Function;

public class GenerateSession implements Function<AuthenticationPassedEvent, SessionTokens> {
    private final AuthorizationDataRepository authorizationDataRepository;
    private final Clock clock;
    private final int refreshTokenValidityHours;
    private final int accessTokenValidityHours;

    public GenerateSession(AuthorizationDataRepository authorizationDataRepository, Clock clock,
                           int refreshTokenValidityHours, int accessTokenValidityHours) {
        this.authorizationDataRepository = authorizationDataRepository;
        this.clock = clock;
        this.refreshTokenValidityHours = refreshTokenValidityHours;
        this.accessTokenValidityHours = accessTokenValidityHours;
    }

    @Override
    public SessionTokens apply(AuthenticationPassedEvent authenticationPassedEvent) {
        return authorizationDataRepository.create(
                SessionTokens.createFor(
                        authenticationPassedEvent.email(),
                        refreshTokenValidityHours,
                        accessTokenValidityHours,
                        clock
                )
        );
    }
}

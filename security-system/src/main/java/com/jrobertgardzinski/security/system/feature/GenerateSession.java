package com.jrobertgardzinski.security.system.feature;

import com.jrobertgardzinski.security.domain.vo.SessionTokensConfig;
import com.jrobertgardzinski.security.domain.entity.SessionTokens;
import com.jrobertgardzinski.security.domain.event.authentication.AuthenticationPassedEvent;
import com.jrobertgardzinski.security.domain.repository.AuthorizationDataRepository;

import java.time.Clock;
import java.util.function.Function;

public class GenerateSession implements Function<AuthenticationPassedEvent, SessionTokens> {
    private final AuthorizationDataRepository authorizationDataRepository;
    private final Clock clock;
    private final SessionTokensConfig config;

    public GenerateSession(AuthorizationDataRepository authorizationDataRepository, Clock clock, SessionTokensConfig config) {
        this.authorizationDataRepository = authorizationDataRepository;
        this.clock = clock;
        this.config = config;
    }

    @Override
    public SessionTokens apply(AuthenticationPassedEvent authenticationPassedEvent) {
        return authorizationDataRepository.create(
                SessionTokens.createFor(
                        authenticationPassedEvent.email(),
                        config,
                        clock
                )
        );
    }
}

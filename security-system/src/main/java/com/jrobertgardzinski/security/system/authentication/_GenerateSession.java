package com.jrobertgardzinski.security.system.authentication;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.entity.SessionTokens;
import com.jrobertgardzinski.security.domain.repository.AuthorizationDataRepository;
import com.jrobertgardzinski.security.domain.vo.SessionFamily;
import com.jrobertgardzinski.security.domain.vo.SessionTokensConfig;

import java.time.Clock;

class _GenerateSession {
    private final AuthorizationDataRepository authorizationDataRepository;
    private final Clock clock;
    private final SessionTokensConfig config;

    public _GenerateSession(AuthorizationDataRepository authorizationDataRepository, Clock clock, SessionTokensConfig config) {
        this.authorizationDataRepository = authorizationDataRepository;
        this.clock = clock;
        this.config = config;
    }

    public SessionTokens create(Email email) {
        // each authentication starts a fresh session lineage
        return authorizationDataRepository.create(SessionTokens.createFor(email, config, clock), SessionFamily.start());
    }
}

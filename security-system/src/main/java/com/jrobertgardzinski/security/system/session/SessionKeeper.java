package com.jrobertgardzinski.security.system.session;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.core.IssuedSession;
import com.jrobertgardzinski.security.domain.core.Sessions;
import com.jrobertgardzinski.security.domain.session.AccessTokenMint;
import com.jrobertgardzinski.security.domain.session.SessionFamily;
import com.jrobertgardzinski.security.domain.session.SessionRepository;
import com.jrobertgardzinski.security.domain.session.SessionTokens;
import com.jrobertgardzinski.security.domain.session.SessionTokensConfig;

import java.time.Clock;

/**
 * The session area's answer to {@link Sessions}: a sign-in opens a session — a new family, so the
 * first rotation has nothing to be mistaken for — and a change that should outlive none ends them.
 */
public final class SessionKeeper implements Sessions {

    private final SessionRepository sessionRepository;
    private final Clock clock;
    private final SessionTokensConfig config;
    private final AccessTokenMint accessTokenMint;

    public SessionKeeper(SessionRepository sessionRepository, Clock clock, SessionTokensConfig config,
                         AccessTokenMint accessTokenMint) {
        this.sessionRepository = sessionRepository;
        this.clock = clock;
        this.config = config;
        this.accessTokenMint = accessTokenMint;
    }

    @Override
    public IssuedSession open(Email email) {
        SessionTokens tokens = sessionRepository.create(
                SessionTokens.createFor(email, config, clock, accessTokenMint), SessionFamily.start());
        return new IssuedSession(tokens.plainAccessToken(), tokens.plainRefreshToken());
    }

    @Override
    public void endAll(Email email) {
        sessionRepository.revokeAllSessions(email);
    }
}

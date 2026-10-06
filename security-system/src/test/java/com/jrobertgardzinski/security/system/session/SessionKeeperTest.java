package com.jrobertgardzinski.security.system.session;

import com.jrobertgardzinski.security.domain.core.IssuedSession;
import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.session.SessionTokens;
import com.jrobertgardzinski.security.domain.session.SessionRepository;
import com.jrobertgardzinski.security.domain.session.AccessTokenValidityInHours;
import com.jrobertgardzinski.security.domain.session.RefreshTokenValidityInHours;
import com.jrobertgardzinski.security.domain.session.SessionTokensConfig;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import net.jqwik.api.Example;
import net.jqwik.api.Label;
import net.jqwik.api.lifecycle.BeforeTry;
import org.mockito.Mockito;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Epic("Use case")
@Feature("Authentication")
@Story("Generate session")
class SessionKeeperTest {

    private static final Email EMAIL = Email.of("user@example.com");
    private static final SessionTokensConfig CONFIG = new SessionTokensConfig(
            new RefreshTokenValidityInHours(24),
            new AccessTokenValidityInHours(1));
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    private SessionRepository sessionRepository;
    private SessionKeeper sessionKeeper;

    @BeforeTry
    void init() {
        sessionRepository = Mockito.mock(SessionRepository.class);
        sessionKeeper = new SessionKeeper(sessionRepository, CLOCK, CONFIG, com.jrobertgardzinski.security.domain.session.AccessTokenMint.RANDOM);
    }

    @Example
    @Label("Opens a session for the email and hands back the persisted tokens in plain form")
    void opens_a_session_for_email() {
        SessionTokens persisted = SessionTokens.createFor(EMAIL, CONFIG, CLOCK, com.jrobertgardzinski.security.domain.session.AccessTokenMint.RANDOM);
        Mockito.when(sessionRepository.create(Mockito.any(), Mockito.any())).thenReturn(persisted);

        IssuedSession result = sessionKeeper.open(EMAIL);

        assertAll(
                () -> assertEquals(new IssuedSession(persisted.plainAccessToken(), persisted.plainRefreshToken()), result),
                () -> Mockito.verify(sessionRepository).create(Mockito.any(), Mockito.any())
        );
    }

    @Example
    @Label("Ends every session of the email")
    void ends_all_sessions() {
        sessionKeeper.endAll(EMAIL);

        Mockito.verify(sessionRepository).revokeAllSessions(EMAIL);
    }
}

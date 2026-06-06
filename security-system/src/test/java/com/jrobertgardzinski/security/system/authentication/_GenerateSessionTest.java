package com.jrobertgardzinski.security.system.authentication;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.entity.SessionTokens;
import com.jrobertgardzinski.security.domain.repository.AuthorizationDataRepository;
import com.jrobertgardzinski.security.domain.vo.AccessTokenValidityInHours;
import com.jrobertgardzinski.security.domain.vo.RefreshTokenValidityInHours;
import com.jrobertgardzinski.security.domain.vo.SessionTokensConfig;
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
class _GenerateSessionTest {

    private static final Email EMAIL = Email.of("user@example.com");
    private static final SessionTokensConfig CONFIG = new SessionTokensConfig(
            new RefreshTokenValidityInHours(24),
            new AccessTokenValidityInHours(1));
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    private AuthorizationDataRepository authorizationDataRepository;
    private _GenerateSession generateSession;

    @BeforeTry
    void init() {
        authorizationDataRepository = Mockito.mock(AuthorizationDataRepository.class);
        generateSession = new _GenerateSession(authorizationDataRepository, CLOCK, CONFIG);
    }

    @Example
    @Label("Creates session tokens for the email and returns the persisted result")
    void creates_session_tokens_for_email() {
        SessionTokens persisted = SessionTokens.createFor(EMAIL, CONFIG, CLOCK);
        Mockito.when(authorizationDataRepository.create(Mockito.any())).thenReturn(persisted);

        SessionTokens result = generateSession.create(EMAIL);

        assertAll(
                () -> assertEquals(persisted, result),
                () -> Mockito.verify(authorizationDataRepository).create(Mockito.any())
        );
    }
}

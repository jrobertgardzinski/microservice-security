package com.jrobertgardzinski.security.system.session;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.entity.SessionTokens;
import com.jrobertgardzinski.security.domain.repository.AuthorizationDataRepository;
import com.jrobertgardzinski.security.domain.vo.AccessTokenValidityInHours;
import com.jrobertgardzinski.security.domain.vo.RefreshTokenValidityInHours;
import com.jrobertgardzinski.security.domain.vo.SessionRefreshRequest;
import com.jrobertgardzinski.security.domain.vo.SessionTokensConfig;
import com.jrobertgardzinski.security.domain.vo.token.RefreshToken;
import com.jrobertgardzinski.security.domain.vo.token.expiration.RefreshTokenExpiration;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import net.jqwik.api.Example;
import net.jqwik.api.Label;
import net.jqwik.api.lifecycle.BeforeTry;
import org.mockito.Mockito;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@Epic("Use case")
@Feature("Refresh session")
class RefreshSessionTest {

    record Given(Email email, RefreshToken refreshToken, SessionRefreshRequest request) {}
    private static final Given GIVEN = given();
    private static Given given() {
        Email email = Email.of("user@example.com");
        RefreshToken refreshToken = new RefreshToken("refresh-token");
        return new Given(email, refreshToken, new SessionRefreshRequest(email, refreshToken));
    }

    private static final SessionTokensConfig CONFIG = new SessionTokensConfig(
            new RefreshTokenValidityInHours(24),
            new AccessTokenValidityInHours(1));
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    private AuthorizationDataRepository authorizationDataRepository;
    private RefreshSession refreshSession;

    @BeforeTry
    void init() {
        authorizationDataRepository = Mockito.mock(AuthorizationDataRepository.class);
        refreshSession = new RefreshSession(authorizationDataRepository, CLOCK, CONFIG);
    }

    @Example
    @Label("Passed when refresh token is found and not expired")
    void passed_when_token_found_and_not_expired() {
        RefreshTokenExpiration expiration = Mockito.mock(RefreshTokenExpiration.class);
        Mockito.when(expiration.hasExpired(CLOCK)).thenReturn(false);
        SessionTokens createdTokens = SessionTokens.createFor(GIVEN.email, CONFIG, CLOCK);
        Mockito.when(authorizationDataRepository.findRefreshTokenExpirationBy(GIVEN.email, GIVEN.refreshToken))
                .thenReturn(expiration);
        Mockito.when(authorizationDataRepository.create(Mockito.any()))
                .thenReturn(createdTokens);

        RefreshSessionResult result = refreshSession.execute(GIVEN.request);

        RefreshSessionResult.Passed passed = assertInstanceOf(RefreshSessionResult.Passed.class, result);
        assertAll(
                () -> assertEquals(createdTokens, passed.sessionTokens()),
                () -> Mockito.verify(authorizationDataRepository).deleteBy(GIVEN.email),
                () -> Mockito.verify(authorizationDataRepository).create(Mockito.any())
        );
    }

    @Example
    @Label("Expired when refresh token is found but expired")
    void expired_when_token_found_but_expired() {
        RefreshTokenExpiration expiration = Mockito.mock(RefreshTokenExpiration.class);
        Mockito.when(expiration.hasExpired(CLOCK))
                .thenReturn(true);
        Mockito.when(authorizationDataRepository.findRefreshTokenExpirationBy(GIVEN.email, GIVEN.refreshToken))
                .thenReturn(expiration);

        RefreshSessionResult result = refreshSession.execute(GIVEN.request);

        RefreshSessionResult.Expired expired = assertInstanceOf(RefreshSessionResult.Expired.class, result);
        assertAll(
                () -> assertEquals(GIVEN.email, expired.email()),
                () -> Mockito.verify(authorizationDataRepository).deleteBy(GIVEN.email),
                () -> Mockito.verify(authorizationDataRepository, Mockito.never()).create(Mockito.any())
        );
    }

    @Example
    @Label("NotFound when no refresh token is stored")
    void not_found_when_no_token() {
        Mockito.when(authorizationDataRepository.findRefreshTokenExpirationBy(GIVEN.email, GIVEN.refreshToken))
                .thenReturn(null);

        RefreshSessionResult result = refreshSession.execute(GIVEN.request);

        RefreshSessionResult.NotFound notFound = assertInstanceOf(RefreshSessionResult.NotFound.class, result);
        assertAll(
                () -> assertEquals(GIVEN.email, notFound.email()),
                () -> Mockito.verify(authorizationDataRepository, Mockito.never()).deleteBy(Mockito.any()),
                () -> Mockito.verify(authorizationDataRepository, Mockito.never()).create(Mockito.any())
        );
    }
}

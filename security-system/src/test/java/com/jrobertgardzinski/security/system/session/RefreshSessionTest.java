package com.jrobertgardzinski.security.system.session;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.entity.SessionTokens;
import com.jrobertgardzinski.security.domain.repository.AuthorizationDataRepository;
import com.jrobertgardzinski.security.domain.vo.AccessTokenValidityInHours;
import com.jrobertgardzinski.security.domain.vo.RefreshTokenValidityInHours;
import com.jrobertgardzinski.security.domain.vo.SessionFamily;
import com.jrobertgardzinski.security.domain.vo.SessionRefreshRequest;
import com.jrobertgardzinski.security.domain.vo.SessionStatus;
import com.jrobertgardzinski.security.domain.vo.SessionTokensConfig;
import com.jrobertgardzinski.security.domain.vo.StoredSession;
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
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

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
        return new Given(email, refreshToken, new SessionRefreshRequest(refreshToken));
    }
    private static final SessionFamily FAMILY = new SessionFamily(UUID.fromString("00000000-0000-0000-0000-000000000001"));

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
    @Label("Refreshed: an active, unexpired token rotates to a new one in the same family")
    void refreshed_when_active_and_not_expired() {
        StoredSession session = storedSession(LocalDateTime.now(CLOCK).plusHours(1), SessionStatus.ACTIVE);
        SessionTokens createdTokens = SessionTokens.createFor(GIVEN.email, CONFIG, CLOCK);
        Mockito.when(authorizationDataRepository.findByRefreshToken(GIVEN.refreshToken))
                .thenReturn(Optional.of(session));
        Mockito.when(authorizationDataRepository.create(Mockito.any(), Mockito.eq(FAMILY)))
                .thenReturn(createdTokens);

        RefreshSessionResult result = refreshSession.execute(GIVEN.request);

        RefreshSessionResult.Refreshed refreshed = assertInstanceOf(RefreshSessionResult.Refreshed.class, result);
        assertAll(
                () -> assertEquals(createdTokens, refreshed.sessionTokens()),
                () -> Mockito.verify(authorizationDataRepository).markRotated(GIVEN.refreshToken),
                () -> Mockito.verify(authorizationDataRepository).create(Mockito.any(), Mockito.eq(FAMILY)),
                () -> Mockito.verify(authorizationDataRepository, Mockito.never()).revokeFamily(Mockito.any())
        );
    }

    @Example
    @Label("Expired: an active but expired token is rejected, nothing is rotated")
    void expired_when_active_but_expired() {
        StoredSession session = storedSession(LocalDateTime.now(CLOCK).minusHours(1), SessionStatus.ACTIVE);
        Mockito.when(authorizationDataRepository.findByRefreshToken(GIVEN.refreshToken))
                .thenReturn(Optional.of(session));

        RefreshSessionResult result = refreshSession.execute(GIVEN.request);

        RefreshSessionResult.Expired expired = assertInstanceOf(RefreshSessionResult.Expired.class, result);
        assertAll(
                () -> assertEquals(GIVEN.email, expired.email()),
                () -> Mockito.verify(authorizationDataRepository, Mockito.never()).markRotated(Mockito.any()),
                () -> Mockito.verify(authorizationDataRepository, Mockito.never()).create(Mockito.any(), Mockito.any())
        );
    }

    @Example
    @Label("NotFound: no session matches the refresh token")
    void not_found_when_no_session() {
        Mockito.when(authorizationDataRepository.findByRefreshToken(GIVEN.refreshToken))
                .thenReturn(Optional.empty());

        RefreshSessionResult result = refreshSession.execute(GIVEN.request);

        assertInstanceOf(RefreshSessionResult.NotFound.class, result);
        Mockito.verify(authorizationDataRepository, Mockito.never()).create(Mockito.any(), Mockito.any());
    }

    @Example
    @Label("ReuseDetected: replaying a rotated token revokes the whole family")
    void reuse_detected_when_token_already_rotated() {
        StoredSession session = storedSession(LocalDateTime.now(CLOCK).plusHours(1), SessionStatus.ROTATED);
        Mockito.when(authorizationDataRepository.findByRefreshToken(GIVEN.refreshToken))
                .thenReturn(Optional.of(session));

        RefreshSessionResult result = refreshSession.execute(GIVEN.request);

        assertInstanceOf(RefreshSessionResult.ReuseDetected.class, result);
        assertAll(
                () -> Mockito.verify(authorizationDataRepository).revokeFamily(FAMILY),
                () -> Mockito.verify(authorizationDataRepository, Mockito.never()).create(Mockito.any(), Mockito.any())
        );
    }

    private static StoredSession storedSession(LocalDateTime refreshExpiry, SessionStatus status) {
        return new StoredSession(GIVEN.email, new RefreshTokenExpiration(refreshExpiry), FAMILY, status);
    }
}

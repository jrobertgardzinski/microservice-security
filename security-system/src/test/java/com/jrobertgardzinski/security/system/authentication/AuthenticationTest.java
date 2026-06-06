package com.jrobertgardzinski.security.system.authentication;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.password.domain.PlaintextPassword;
import com.jrobertgardzinski.security.domain.entity.AuthenticationBlock;
import com.jrobertgardzinski.security.domain.entity.SessionTokens;
import com.jrobertgardzinski.security.domain.event.AuthenticationEvent;
import com.jrobertgardzinski.security.domain.event.BruteForceProtectionEvent;
import com.jrobertgardzinski.security.domain.vo.AccessTokenValidityInHours;
import com.jrobertgardzinski.security.domain.vo.AuthenticationRequest;
import com.jrobertgardzinski.security.domain.vo.Credentials;
import com.jrobertgardzinski.security.domain.vo.IpAddress;
import com.jrobertgardzinski.security.domain.vo.RefreshTokenValidityInHours;
import com.jrobertgardzinski.security.domain.vo.SessionTokensConfig;
import com.jrobertgardzinski.security.system.testkit.Concept;
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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@Epic("Use case")
@Feature("Authentication")
class AuthenticationTest {

    record Given(IpAddress ipAddress, Email email, PlaintextPassword password,
                 AuthenticationRequest request, Credentials credentials) {}
    private static final Given GIVEN = given();
    private static Given given() {
        IpAddress ipAddress = new IpAddress("192.168.0.1");
        Email email = Email.of("user@example.com");
        PlaintextPassword password = PlaintextPassword.of("plaintext");
        return new Given(ipAddress, email, password,
                new AuthenticationRequest(ipAddress, email, password),
                new Credentials(email, password));
    }

    private static final SessionTokensConfig CONFIG = new SessionTokensConfig(
            new RefreshTokenValidityInHours(24),
            new AccessTokenValidityInHours(1));
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    private _BruteForceGuard bruteForceGuard;
    private _VerifyCredentials verifyCredentials;
    private _GenerateSession generateSession;
    private _CleanBruteForceRecords cleanBruteForceRecords;
    private _UpdateBruteForceRecords updateBruteForceRecords;
    private Authentication authentication;

    @BeforeTry
    void init() {
        bruteForceGuard = Mockito.mock(_BruteForceGuard.class);
        verifyCredentials = Mockito.mock(_VerifyCredentials.class);
        generateSession = Mockito.mock(_GenerateSession.class);
        cleanBruteForceRecords = Mockito.mock(_CleanBruteForceRecords.class);
        updateBruteForceRecords = Mockito.mock(_UpdateBruteForceRecords.class);
        authentication = new Authentication(
                bruteForceGuard, verifyCredentials, generateSession,
                cleanBruteForceRecords, updateBruteForceRecords);
    }

    @Example
    @Label("Blocked when the brute-force guard blocks the IP")
    @Concept("brute-force-guard")
    void blocked_when_guard_blocks() {
        AuthenticationBlock block = new AuthenticationBlock(GIVEN.ipAddress, LocalDateTime.now(CLOCK).plusMinutes(15));
        Mockito.when(bruteForceGuard.execute(GIVEN.ipAddress))
                .thenReturn(new BruteForceProtectionEvent.Blocked(block));

        AuthenticationResult result = authentication.execute(GIVEN.request);

        AuthenticationResult.Blocked blocked = assertInstanceOf(AuthenticationResult.Blocked.class, result);
        assertAll(
                () -> assertEquals(block, blocked.authenticationBlock()),
                () -> Mockito.verifyNoInteractions(verifyCredentials),
                () -> Mockito.verifyNoInteractions(generateSession),
                () -> Mockito.verifyNoInteractions(cleanBruteForceRecords),
                () -> Mockito.verifyNoInteractions(updateBruteForceRecords)
        );
    }

    @Example
    @Label("Passed when the guard allows and credentials are valid")
    @Concept("credential-verification")
    void passed_when_guard_allows_and_credentials_valid() {
        SessionTokens sessionTokens = SessionTokens.createFor(GIVEN.email, CONFIG, CLOCK);
        Mockito.when(bruteForceGuard.execute(GIVEN.ipAddress))
                .thenReturn(new BruteForceProtectionEvent.Passed());
        Mockito.when(verifyCredentials.execute(GIVEN.credentials))
                .thenReturn(new AuthenticationEvent.Passed(GIVEN.email));
        Mockito.when(generateSession.create(GIVEN.email)).thenReturn(sessionTokens);

        AuthenticationResult result = authentication.execute(GIVEN.request);

        AuthenticationResult.Passed passed = assertInstanceOf(AuthenticationResult.Passed.class, result);
        assertAll(
                () -> assertEquals(sessionTokens, passed.session()),
                () -> Mockito.verify(cleanBruteForceRecords).execute(GIVEN.ipAddress),
                () -> Mockito.verify(generateSession).create(GIVEN.email),
                () -> Mockito.verify(updateBruteForceRecords, Mockito.never()).execute(Mockito.any())
        );
    }

    @Example
    @Label("Failed when the guard allows but credentials are invalid")
    @Concept("credential-verification")
    void failed_when_guard_allows_but_credentials_invalid() {
        Mockito.when(bruteForceGuard.execute(GIVEN.ipAddress))
                .thenReturn(new BruteForceProtectionEvent.Passed());
        Mockito.when(verifyCredentials.execute(GIVEN.credentials))
                .thenReturn(new AuthenticationEvent.Failed(GIVEN.email));

        AuthenticationResult result = authentication.execute(GIVEN.request);

        assertInstanceOf(AuthenticationResult.Failed.class, result);
        assertAll(
                () -> Mockito.verify(updateBruteForceRecords).execute(GIVEN.ipAddress),
                () -> Mockito.verify(cleanBruteForceRecords, Mockito.never()).execute(Mockito.any()),
                () -> Mockito.verify(generateSession, Mockito.never()).create(Mockito.any())
        );
    }
}

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
import com.jrobertgardzinski.security.domain.vo.Source;
import com.jrobertgardzinski.security.domain.vo.RefreshTokenValidityInHours;
import com.jrobertgardzinski.security.domain.vo.SessionTokensConfig;
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

    record Given(Source ipAddress, Email email, PlaintextPassword password,
                 AuthenticationRequest request, Credentials credentials) {}
    private static final Given GIVEN = given();
    private static Given given() {
        Source ipAddress = Source.of(new IpAddress("192.168.0.1"));
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
    private _RequireVerifiedEmail requireVerifiedEmail;
    private _GenerateSession generateSession;
    private _CleanBruteForceRecords cleanBruteForceRecords;
    private _UpdateBruteForceRecords updateBruteForceRecords;
    private com.jrobertgardzinski.security.domain.repository.EnrolledFactorRepository enrolledFactors;
    private Authentication authentication;

    @BeforeTry
    void init() {
        bruteForceGuard = Mockito.mock(_BruteForceGuard.class);
        verifyCredentials = Mockito.mock(_VerifyCredentials.class);
        requireVerifiedEmail = Mockito.mock(_RequireVerifiedEmail.class);
        // most examples exercise a completed onboarding; the unverified example overrides this
        Mockito.when(requireVerifiedEmail.isVerified(Mockito.any())).thenReturn(true);
        generateSession = Mockito.mock(_GenerateSession.class);
        cleanBruteForceRecords = Mockito.mock(_CleanBruteForceRecords.class);
        updateBruteForceRecords = Mockito.mock(_UpdateBruteForceRecords.class);
        // no factors enrolled in these examples → the chain is empty and sign-in is single-factor
        enrolledFactors = Mockito.mock(com.jrobertgardzinski.security.domain.repository.EnrolledFactorRepository.class);
        Mockito.when(enrolledFactors.findByUser(Mockito.any())).thenReturn(java.util.List.of());
        var mfaChain = new com.jrobertgardzinski.security.system.mfa.MfaChain(
                new com.jrobertgardzinski.security.system.mfa.FactorRegistry(java.util.List.of()),
                com.jrobertgardzinski.security.config.mfa.ChallengeCodeConfig.withDefaults(), CLOCK, 10);
        var pendingStore = Mockito.mock(com.jrobertgardzinski.security.system.mfa.PendingAuthenticationStore.class);
        authentication = new Authentication(
                bruteForceGuard, verifyCredentials, requireVerifiedEmail, generateSession,
                cleanBruteForceRecords, updateBruteForceRecords,
                enrolledFactors, mfaChain, pendingStore);
    }

    @Example
    @Label("Blocked when the brute-force guard blocks the IP")
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
    @Label("Authenticated when the guard allows and credentials are valid")
    void authenticated_when_guard_allows_and_credentials_valid() {
        SessionTokens sessionTokens = SessionTokens.createFor(GIVEN.email, CONFIG, CLOCK);
        Mockito.when(bruteForceGuard.execute(GIVEN.ipAddress))
                .thenReturn(new BruteForceProtectionEvent.Allowed());
        Mockito.when(verifyCredentials.execute(GIVEN.credentials))
                .thenReturn(new AuthenticationEvent.Valid(GIVEN.email));
        Mockito.when(generateSession.create(GIVEN.email)).thenReturn(sessionTokens);

        AuthenticationResult result = authentication.execute(GIVEN.request);

        AuthenticationResult.Authenticated authenticated = assertInstanceOf(AuthenticationResult.Authenticated.class, result);
        assertAll(
                () -> assertEquals(sessionTokens, authenticated.session()),
                () -> Mockito.verify(cleanBruteForceRecords).execute(GIVEN.ipAddress),
                () -> Mockito.verify(generateSession).create(GIVEN.email),
                () -> Mockito.verify(updateBruteForceRecords, Mockito.never()).execute(Mockito.any())
        );
    }

    @Example
    @Label("Rejected when the guard allows but credentials are invalid")
    void rejected_when_guard_allows_but_credentials_invalid() {
        Mockito.when(bruteForceGuard.execute(GIVEN.ipAddress))
                .thenReturn(new BruteForceProtectionEvent.Allowed());
        Mockito.when(verifyCredentials.execute(GIVEN.credentials))
                .thenReturn(new AuthenticationEvent.Invalid(GIVEN.email));

        AuthenticationResult result = authentication.execute(GIVEN.request);

        assertInstanceOf(AuthenticationResult.Rejected.class, result);
        assertAll(
                () -> Mockito.verify(updateBruteForceRecords).execute(GIVEN.ipAddress),
                () -> Mockito.verify(cleanBruteForceRecords, Mockito.never()).execute(Mockito.any()),
                () -> Mockito.verify(generateSession, Mockito.never()).create(Mockito.any())
        );
    }

    @Example
    @Label("Email-not-verified when credentials are valid but the address is unverified")
    void email_not_verified_when_address_unverified() {
        Mockito.when(bruteForceGuard.execute(GIVEN.ipAddress))
                .thenReturn(new BruteForceProtectionEvent.Allowed());
        Mockito.when(verifyCredentials.execute(GIVEN.credentials))
                .thenReturn(new AuthenticationEvent.Valid(GIVEN.email));
        Mockito.when(requireVerifiedEmail.isVerified(GIVEN.email)).thenReturn(false);

        AuthenticationResult result = authentication.execute(GIVEN.request);

        assertInstanceOf(AuthenticationResult.EmailNotVerified.class, result);
        assertAll(
                () -> Mockito.verify(generateSession, Mockito.never()).create(Mockito.any()),
                () -> Mockito.verify(cleanBruteForceRecords, Mockito.never()).execute(Mockito.any()),
                () -> Mockito.verify(updateBruteForceRecords, Mockito.never()).execute(Mockito.any())
        );
    }
}

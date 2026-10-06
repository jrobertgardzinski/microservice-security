package com.jrobertgardzinski.security.system.authentication;

import com.jrobertgardzinski.security.domain.core.FailedSignIns;
import com.jrobertgardzinski.security.domain.core.IssuedSession;
import com.jrobertgardzinski.security.domain.core.SecondFactors;
import com.jrobertgardzinski.security.domain.core.Sessions;
import com.jrobertgardzinski.security.domain.core.VerifiedAddresses;
import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.password.domain.PlaintextPassword;
import com.jrobertgardzinski.security.domain.authentication.AuthenticationBlock;
import com.jrobertgardzinski.security.domain.authentication.AuthenticationEvent;
import com.jrobertgardzinski.security.domain.authentication.BruteForceProtectionEvent;
import com.jrobertgardzinski.security.domain.session.AccessTokenValidityInHours;
import com.jrobertgardzinski.security.domain.authentication.AuthenticationRequest;
import com.jrobertgardzinski.security.domain.authentication.Credentials;
import com.jrobertgardzinski.security.domain.core.IpAddress;
import com.jrobertgardzinski.security.domain.core.AttemptedAccount;
import com.jrobertgardzinski.security.domain.core.LockoutSubject;
import com.jrobertgardzinski.security.domain.core.Source;
import com.jrobertgardzinski.security.domain.session.RefreshTokenValidityInHours;
import com.jrobertgardzinski.security.domain.session.SessionTokensConfig;
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
    private VerifiedAddresses requireVerifiedEmail;
    private Sessions generateSession;
    private _CleanBruteForceRecords cleanBruteForceRecords;
    private FailedSignIns updateBruteForceRecords;
    private SecondFactors secondFactors;
    private Authentication authentication;

    @BeforeTry
    void init() {
        bruteForceGuard = Mockito.mock(_BruteForceGuard.class);
        verifyCredentials = Mockito.mock(_VerifyCredentials.class);
        requireVerifiedEmail = Mockito.mock(VerifiedAddresses.class);
        // most examples exercise a completed onboarding; the unverified example overrides this
        Mockito.when(requireVerifiedEmail.isVerified(Mockito.any())).thenReturn(true);
        generateSession = Mockito.mock(Sessions.class);
        cleanBruteForceRecords = Mockito.mock(_CleanBruteForceRecords.class);
        updateBruteForceRecords = Mockito.mock(FailedSignIns.class);
        // no factors enrolled in these examples → the chain is empty and sign-in is single-factor
        secondFactors = Mockito.mock(SecondFactors.class);
        Mockito.when(secondFactors.challenge(Mockito.any(), Mockito.any())).thenReturn(java.util.Optional.empty());
        authentication = new Authentication(
                bruteForceGuard, verifyCredentials, requireVerifiedEmail, generateSession,
                cleanBruteForceRecords, updateBruteForceRecords, secondFactors);
    }

    @Example
    @Label("Blocked when the brute-force guard blocks the IP")
    void blocked_when_guard_blocks() {
        AuthenticationBlock block = new AuthenticationBlock(GIVEN.ipAddress, LocalDateTime.now(CLOCK).plusMinutes(15));
        Mockito.when(bruteForceGuard.execute(Mockito.any()))
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
        IssuedSession sessionTokens = new IssuedSession("access", "refresh");
        Mockito.when(bruteForceGuard.execute(Mockito.any()))
                .thenReturn(new BruteForceProtectionEvent.Allowed());
        Mockito.when(verifyCredentials.execute(GIVEN.credentials))
                .thenReturn(new AuthenticationEvent.Valid(GIVEN.email));
        Mockito.when(generateSession.open(GIVEN.email)).thenReturn(sessionTokens);

        AuthenticationResult result = authentication.execute(GIVEN.request);

        AuthenticationResult.Authenticated authenticated = assertInstanceOf(AuthenticationResult.Authenticated.class, result);
        assertAll(
                () -> assertEquals(sessionTokens, authenticated.session()),
                // A successful sign-in clears THIS PAIR's failures and nothing else. The assertion
                // used to be the opposite — "must NOT clear" — and it was right about the danger
                // (P18 poz. 6: clearing the whole ADDRESS let one known-good credential reset the
                // counter for every account behind it) while being wrong about the remedy: with
                // nothing ever cleared, three typos locked out an office, a CGNAT, a CI runner.
                // Narrowed to the pair, the escape valve is back and the amnesty is not.
                () -> Mockito.verify(cleanBruteForceRecords).execute(
                        new LockoutSubject(GIVEN.ipAddress, AttemptedAccount.of(GIVEN.email))),
                () -> Mockito.verify(generateSession).open(GIVEN.email),
                () -> Mockito.verify(updateBruteForceRecords, Mockito.never()).record(Mockito.any())
        );
    }

    @Example
    @Label("Rejected when the guard allows but credentials are invalid")
    void rejected_when_guard_allows_but_credentials_invalid() {
        Mockito.when(bruteForceGuard.execute(Mockito.any()))
                .thenReturn(new BruteForceProtectionEvent.Allowed());
        Mockito.when(verifyCredentials.execute(GIVEN.credentials))
                .thenReturn(new AuthenticationEvent.Invalid(GIVEN.email));

        AuthenticationResult result = authentication.execute(GIVEN.request);

        assertInstanceOf(AuthenticationResult.Rejected.class, result);
        assertAll(
                // charged to the PAIR now: this address against the account that was aimed at
                () -> Mockito.verify(updateBruteForceRecords).record(
                        new LockoutSubject(GIVEN.ipAddress, AttemptedAccount.of(GIVEN.email))),
                () -> Mockito.verify(cleanBruteForceRecords, Mockito.never()).execute(Mockito.any()),
                () -> Mockito.verify(generateSession, Mockito.never()).open(Mockito.any())
        );
    }

    @Example
    @Label("Email-not-verified when credentials are valid but the address is unverified")
    void email_not_verified_when_address_unverified() {
        Mockito.when(bruteForceGuard.execute(Mockito.any()))
                .thenReturn(new BruteForceProtectionEvent.Allowed());
        Mockito.when(verifyCredentials.execute(GIVEN.credentials))
                .thenReturn(new AuthenticationEvent.Valid(GIVEN.email));
        Mockito.when(requireVerifiedEmail.isVerified(GIVEN.email)).thenReturn(false);

        AuthenticationResult result = authentication.execute(GIVEN.request);

        assertInstanceOf(AuthenticationResult.EmailNotVerified.class, result);
        assertAll(
                () -> Mockito.verify(generateSession, Mockito.never()).open(Mockito.any()),
                () -> Mockito.verify(cleanBruteForceRecords, Mockito.never()).execute(Mockito.any()),
                () -> Mockito.verify(updateBruteForceRecords, Mockito.never()).record(Mockito.any())
        );
    }
}

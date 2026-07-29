package com.jrobertgardzinski;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.entity.SessionTokens;
import com.jrobertgardzinski.security.domain.vo.AccessTokenValidityInHours;
import com.jrobertgardzinski.security.domain.vo.RefreshTokenValidityInHours;
import com.jrobertgardzinski.security.domain.vo.SessionFamily;
import com.jrobertgardzinski.security.domain.vo.SessionTokensConfig;
import com.jrobertgardzinski.security.domain.vo.token.AccessToken;
import com.jrobertgardzinski.security.domain.vo.token.RefreshToken;
import com.jrobertgardzinski.security.domain.vo.token.expiration.AuthorizationTokenExpiration;
import com.jrobertgardzinski.security.domain.vo.token.expiration.RefreshTokenExpiration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two promises this adapter makes that used to be made only in prose.
 *
 * <p>It is not a test double. {@code @Requires(missingBeans = DataSource.class)} means it is the
 * repository of every deployment run without a datasource, so "it is only for tests" is exactly the
 * assumption that let both of these sit unproven.
 */
class InMemorySessionLineageTest {

    private static final Email USER = Email.of("user@example.com");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-29T12:00:00Z"), ZoneOffset.UTC);
    private static final SessionTokensConfig CONFIG = new SessionTokensConfig(
            new RefreshTokenValidityInHours(24), new AccessTokenValidityInHours(1));

    private final InMemoryAuthorizationDataRepository repository =
            new InMemoryAuthorizationDataRepository(CLOCK);

    @Test
    @DisplayName("a revoke cannot land between the rotation and its successor")
    void the_rotation_and_its_successor_are_one_step() throws Exception {
        SessionFamily family = SessionFamily.start();
        SessionTokens original = SessionTokens.createFor(USER, CONFIG, CLOCK);
        repository.create(original, family);

        // The thief's thread: it has detected reuse of this lineage and is revoking the family.
        // If it can run BETWEEN the rotation and the write below, the family is destroyed and the
        // successor is then written into it anyway — a live session inside a lineage this service
        // reports as revoked, which is the theft-detection guarantee failing in the one case it
        // exists for. The commit that announced this as fixed left every method locked separately.
        Thread reuseDetected = new Thread(() -> repository.revokeFamily(family));
        AtomicBoolean shutOut = new AtomicBoolean();

        Optional<SessionTokens> successor = repository.rotateAndCreate(
                original.refreshToken(),
                () -> {
                    // inside the critical section: start the revoker and give it a fair chance
                    reuseDetected.start();
                    try {
                        reuseDetected.join(500);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    }
                    shutOut.set(reuseDetected.isAlive());
                    return SessionTokens.createFor(USER, CONFIG, CLOCK);
                },
                family);
        reuseDetected.join();

        assertTrue(successor.isPresent(), "this caller won the rotation");
        assertTrue(shutOut.get(),
                "revokeFamily ran while the rotation was half-done. The successor written after it"
                        + " is a live session in a family the service believes it destroyed, and no"
                        + " device list or revoke will ever show it.");
        assertTrue(repository.listActiveSessions(USER).isEmpty(),
                "and having waited its turn, the revoke takes the successor with it");
        assertTrue(repository.findByAccessToken(successor.orElseThrow().accessToken()).isEmpty(),
                "including the access token it minted");
    }

    @Test
    @DisplayName("a losing rotation writes nothing at all")
    void a_lost_rotation_creates_no_successor() {
        SessionFamily family = SessionFamily.start();
        SessionTokens original = SessionTokens.createFor(USER, CONFIG, CLOCK);
        repository.create(original, family);
        repository.markRotated(original.refreshToken());   // somebody else got there first

        AtomicBoolean minted = new AtomicBoolean();
        Optional<SessionTokens> successor = repository.rotateAndCreate(
                original.refreshToken(),
                () -> {
                    minted.set(true);
                    return SessionTokens.createFor(USER, CONFIG, CLOCK);
                },
                family);

        assertTrue(successor.isEmpty(), "the caller must be told it lost, and treat that as a replay");
        assertFalse(minted.get(), "and no tokens are minted for a session that is not being created");
        assertTrue(repository.listActiveSessions(USER).isEmpty());
    }

    @Test
    @DisplayName("an expired session is not listed as somewhere the user is signed in")
    void the_session_list_applies_the_clock_and_not_only_the_status() {
        // The reaper keeps expired rows on purpose — for the longer of the refresh window and 24
        // hours — so that a replayed just-expired token still meets reuse detection. Listing on
        // status alone therefore showed a device that stopped working a day ago on the page a user
        // opens exactly when they fear somebody else is on their account, and no revoke could clear
        // it: the lineage was already dead.
        repository.create(sessionExpiringAt(LocalDateTime.now(CLOCK).minusMinutes(1)), SessionFamily.start());
        assertTrue(repository.listActiveSessions(USER).isEmpty(),
                "ACTIVE is a status, not a fact about time");

        repository.create(sessionExpiringAt(LocalDateTime.now(CLOCK).plusMinutes(1)), SessionFamily.start());
        assertEquals(1, repository.listActiveSessions(USER).size(),
                "and a session that still works is still shown");
    }

    private static SessionTokens sessionExpiringAt(LocalDateTime expiry) {
        return new SessionTokens(USER, RefreshToken.random(), new AccessToken("access-" + expiry),
                new RefreshTokenExpiration(expiry),
                new AuthorizationTokenExpiration(expiry));
    }
}

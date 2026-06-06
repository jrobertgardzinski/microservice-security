package com.jrobertgardzinski.security.system.authentication;

import com.jrobertgardzinski.security.config.bruteforce.BruteForceConfig;
import com.jrobertgardzinski.security.domain.entity.AuthenticationBlock;
import com.jrobertgardzinski.security.domain.event.BruteForceProtectionEvent;
import com.jrobertgardzinski.security.domain.repository.AuthenticationBlockRepository;
import com.jrobertgardzinski.security.domain.repository.FailedAuthenticationRepository;
import com.jrobertgardzinski.security.domain.vo.FailuresCount;
import com.jrobertgardzinski.security.domain.vo.IpAddress;
import com.jrobertgardzinski.security.system.testkit.Concept;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import net.jqwik.api.Example;
import net.jqwik.api.Label;
import net.jqwik.api.lifecycle.BeforeTry;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@Epic("Use case")
@Feature("Authentication")
@Story("Brute-force guard")
@Concept("brute-force-guard")
class _BruteForceGuardTest {

    record Given(IpAddress ip) {}
    private static final Given GIVEN = new Given(new IpAddress("192.168.0.1"));

    private static final BruteForceConfig CONFIG = BruteForceConfig.builder()
            .failureWindowMinutes(15)
            .maxFailures(3)
            .minBlockMinutes(3)
            .maxBlockMinutes(10)
            .build();
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    private FailedAuthenticationRepository failedAuthenticationRepository;
    private AuthenticationBlockRepository authenticationBlockRepository;
    private _BruteForceGuard bruteForceGuard;

    @BeforeTry
    void init() {
        failedAuthenticationRepository = Mockito.mock(FailedAuthenticationRepository.class);
        authenticationBlockRepository = Mockito.mock(AuthenticationBlockRepository.class);
        bruteForceGuard = new _BruteForceGuard(
                failedAuthenticationRepository, authenticationBlockRepository, CLOCK, CONFIG);
    }

    @Example
    @Label("Blocked when an active block already exists for the IP")
    void blocked_when_active_block_exists() {
        AuthenticationBlock active = new AuthenticationBlock(GIVEN.ip, LocalDateTime.now(CLOCK).plusMinutes(5));
        Mockito.when(authenticationBlockRepository.findBy(GIVEN.ip)).thenReturn(Optional.of(active));

        BruteForceProtectionEvent event = bruteForceGuard.execute(GIVEN.ip);

        BruteForceProtectionEvent.Blocked blocked =
                assertInstanceOf(BruteForceProtectionEvent.Blocked.class, event);
        assertAll(
                () -> assertEquals(active, blocked.authenticationBlock()),
                () -> Mockito.verify(failedAuthenticationRepository, Mockito.never())
                        .countFailuresBy(Mockito.any(), Mockito.any()),
                () -> Mockito.verify(authenticationBlockRepository, Mockito.never()).create(Mockito.any())
        );
    }

    @Example
    @Label("Blocked and a new block created when the failure limit is reached")
    void blocked_and_new_block_created_when_failure_limit_reached() {
        AuthenticationBlock created = new AuthenticationBlock(GIVEN.ip, LocalDateTime.now(CLOCK).plusMinutes(5));
        Mockito.when(authenticationBlockRepository.findBy(GIVEN.ip)).thenReturn(Optional.empty());
        Mockito.when(failedAuthenticationRepository.countFailuresBy(Mockito.eq(GIVEN.ip), Mockito.any()))
                .thenReturn(new FailuresCount(CONFIG.maxFailures().value()));
        Mockito.when(authenticationBlockRepository.create(Mockito.any())).thenReturn(created);

        BruteForceProtectionEvent event = bruteForceGuard.execute(GIVEN.ip);

        BruteForceProtectionEvent.Blocked blocked =
                assertInstanceOf(BruteForceProtectionEvent.Blocked.class, event);
        ArgumentCaptor<AuthenticationBlock> captor = ArgumentCaptor.forClass(AuthenticationBlock.class);
        Mockito.verify(authenticationBlockRepository).create(captor.capture());
        LocalDateTime until = captor.getValue().expiryDate();
        LocalDateTime base = LocalDateTime.now(CLOCK);
        // block length comes from ThreadLocalRandom (nondeterministic) — assert it lands within
        // [minBlockMinutes, maxBlockMinutes]; hard assertion would need an injected randomness source (see todo).
        assertAll(
                () -> assertEquals(created, blocked.authenticationBlock()),
                () -> Mockito.verify(failedAuthenticationRepository).removeAllFor(GIVEN.ip),
                () -> assertFalse(until.isBefore(base.plusMinutes(CONFIG.minBlockMinutes().value())),
                        "until must be >= base + minBlockMinutes"),
                () -> assertFalse(until.isAfter(base.plusMinutes(CONFIG.maxBlockMinutes().value())),
                        "until must be <= base + maxBlockMinutes")
        );
    }

    @Example
    @Label("Passed when there is no active block and failures are below the limit")
    void passed_when_no_active_block_and_below_limit() {
        Mockito.when(authenticationBlockRepository.findBy(GIVEN.ip)).thenReturn(Optional.empty());
        Mockito.when(failedAuthenticationRepository.countFailuresBy(Mockito.eq(GIVEN.ip), Mockito.any()))
                .thenReturn(new FailuresCount(CONFIG.maxFailures().value() - 1));

        BruteForceProtectionEvent event = bruteForceGuard.execute(GIVEN.ip);

        assertInstanceOf(BruteForceProtectionEvent.Passed.class, event);
        assertAll(
                () -> Mockito.verify(authenticationBlockRepository, Mockito.never()).create(Mockito.any()),
                () -> Mockito.verify(failedAuthenticationRepository, Mockito.never()).removeAllFor(Mockito.any())
        );
    }
}

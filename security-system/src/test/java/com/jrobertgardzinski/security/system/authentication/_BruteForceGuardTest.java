package com.jrobertgardzinski.security.system.authentication;

import com.jrobertgardzinski.security.config.bruteforce.BruteForceConfig;
import com.jrobertgardzinski.security.domain.entity.AuthenticationBlock;
import com.jrobertgardzinski.security.domain.event.BruteForceProtectionEvent;
import com.jrobertgardzinski.security.domain.repository.AuthenticationBlockRepository;
import com.jrobertgardzinski.security.domain.repository.RejectedAuthenticationRepository;
import com.jrobertgardzinski.security.domain.vo.FailuresCount;
import com.jrobertgardzinski.security.domain.vo.IpAddress;
import com.jrobertgardzinski.security.domain.vo.Source;
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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@Epic("Use case")
@Feature("Authentication")
@Story("Brute-force guard")
class _BruteForceGuardTest {

    record Given(Source ip) {}
    private static final Given GIVEN = new Given(Source.of(new IpAddress("192.168.0.1")));

    private static final BruteForceConfig CONFIG = BruteForceConfig.builder()
            .failureWindowMinutes(15)
            .maxFailures(3)
            .minBlockMinutes(3)
            .maxBlockMinutes(10)
            .build();
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private static final int BLOCK_MINUTES = 5;
    private static final BlockDurationPolicy BLOCK_DURATION = () -> BLOCK_MINUTES;

    private RejectedAuthenticationRepository rejectedAuthenticationRepository;
    private AuthenticationBlockRepository authenticationBlockRepository;
    private _BruteForceGuard bruteForceGuard;

    @BeforeTry
    void init() {
        rejectedAuthenticationRepository = Mockito.mock(RejectedAuthenticationRepository.class);
        authenticationBlockRepository = Mockito.mock(AuthenticationBlockRepository.class);
        bruteForceGuard = new _BruteForceGuard(
                rejectedAuthenticationRepository, authenticationBlockRepository, CLOCK, CONFIG, BLOCK_DURATION);
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
                () -> Mockito.verify(rejectedAuthenticationRepository, Mockito.never())
                        .countFailuresBy(Mockito.any(), Mockito.any()),
                () -> Mockito.verify(authenticationBlockRepository, Mockito.never()).create(Mockito.any())
        );
    }

    @Example
    @Label("A rotated user-agent does not dodge the block: identity is the IP, observed context is not")
    void rotated_user_agent_hits_the_same_block() {
        AuthenticationBlock active = new AuthenticationBlock(GIVEN.ip, LocalDateTime.now(CLOCK).plusMinutes(5));
        Mockito.when(authenticationBlockRepository.findBy(GIVEN.ip)).thenReturn(Optional.of(active));

        Source sameIpOtherBrowser = new Source(GIVEN.ip.ipAddress(), "Fancy-New-Agent/99.0");
        BruteForceProtectionEvent event = bruteForceGuard.execute(sameIpOtherBrowser);

        assertInstanceOf(BruteForceProtectionEvent.Blocked.class, event);
        assertAll(
                () -> assertEquals(GIVEN.ip, sameIpOtherBrowser,
                        "sources with one IP are ONE subject regardless of what was observed"),
                () -> assertEquals(GIVEN.ip.hashCode(), sameIpOtherBrowser.hashCode())
        );
    }

    @Example
    @Label("Blocked and a new block created when the failure limit is reached")
    void blocked_and_new_block_created_when_failure_limit_reached() {
        AuthenticationBlock created = new AuthenticationBlock(GIVEN.ip, LocalDateTime.now(CLOCK).plusMinutes(5));
        Mockito.when(authenticationBlockRepository.findBy(GIVEN.ip)).thenReturn(Optional.empty());
        Mockito.when(rejectedAuthenticationRepository.countFailuresBy(Mockito.eq(GIVEN.ip), Mockito.any()))
                .thenReturn(new FailuresCount(CONFIG.maxFailures().value()));
        Mockito.when(authenticationBlockRepository.create(Mockito.any())).thenReturn(created);

        BruteForceProtectionEvent event = bruteForceGuard.execute(GIVEN.ip);

        BruteForceProtectionEvent.Blocked blocked =
                assertInstanceOf(BruteForceProtectionEvent.Blocked.class, event);
        ArgumentCaptor<AuthenticationBlock> captor = ArgumentCaptor.forClass(AuthenticationBlock.class);
        Mockito.verify(authenticationBlockRepository).create(captor.capture());
        LocalDateTime until = captor.getValue().expiryDate();
        LocalDateTime base = LocalDateTime.now(CLOCK);
        // block length now comes from the injected BlockDurationPolicy — deterministic
        assertAll(
                () -> assertEquals(created, blocked.authenticationBlock()),
                () -> Mockito.verify(rejectedAuthenticationRepository).removeAllFor(GIVEN.ip),
                () -> assertEquals(base.plusMinutes(BLOCK_MINUTES), until)
        );
    }

    @Example
    @Label("Allowed when there is no active block and failures are below the limit")
    void allowed_when_no_active_block_and_below_limit() {
        Mockito.when(authenticationBlockRepository.findBy(GIVEN.ip)).thenReturn(Optional.empty());
        Mockito.when(rejectedAuthenticationRepository.countFailuresBy(Mockito.eq(GIVEN.ip), Mockito.any()))
                .thenReturn(new FailuresCount(CONFIG.maxFailures().value() - 1));

        BruteForceProtectionEvent event = bruteForceGuard.execute(GIVEN.ip);

        assertInstanceOf(BruteForceProtectionEvent.Allowed.class, event);
        assertAll(
                () -> Mockito.verify(authenticationBlockRepository, Mockito.never()).create(Mockito.any()),
                () -> Mockito.verify(rejectedAuthenticationRepository, Mockito.never()).removeAllFor(Mockito.any())
        );
    }
}

package com.jrobertgardzinski.security.system.authentication;

import com.jrobertgardzinski.security.config.bruteforce.BruteForceConfig;
import com.jrobertgardzinski.security.domain.entity.AuthenticationBlock;
import com.jrobertgardzinski.security.domain.event.BruteForceProtectionEvent;
import com.jrobertgardzinski.security.domain.repository.AuthenticationBlockRepository;
import com.jrobertgardzinski.security.domain.repository.FailedAuthenticationRepository;
import com.jrobertgardzinski.security.domain.vo.IpAddress;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Brute-force guard: rejects authentication from an {@link IpAddress} that has an active block,
 * or that has reached the failure limit within the configured window (creating a new block).
 *
 * @see <a href="https://jrobertgardzinski.github.io/portfolio/glossary.html#brute-force-guard">Glossary: brute-force guard</a>
 */
class _BruteForceGuard {

    private final FailedAuthenticationRepository failedAuthenticationRepository;
    private final AuthenticationBlockRepository authenticationBlockRepository;
    private final Clock clock;
    private final BruteForceConfig config;

    public _BruteForceGuard(FailedAuthenticationRepository failedAuthenticationRepository,
                            AuthenticationBlockRepository authenticationBlockRepository,
                            Clock clock, BruteForceConfig config) {
        this.failedAuthenticationRepository = failedAuthenticationRepository;
        this.authenticationBlockRepository = authenticationBlockRepository;
        this.clock = clock;
        this.config = config;
    }

    public BruteForceProtectionEvent execute(IpAddress ipAddress) {
        return existingActiveBlockFor(ipAddress)
                .<BruteForceProtectionEvent>map(BruteForceProtectionEvent.Blocked::new)
                .orElseGet(() -> failureLimitReachedFor(ipAddress)
                        ? new BruteForceProtectionEvent.Blocked(createNewBlockFor(ipAddress))
                        : new BruteForceProtectionEvent.Passed());
    }

    private Optional<AuthenticationBlock> existingActiveBlockFor(IpAddress ipAddress) {
        return authenticationBlockRepository.findBy(ipAddress)
                .filter(block -> block.isStillActive(clock));
    }

    private boolean failureLimitReachedFor(IpAddress ipAddress) {
        LocalDateTime since = LocalDateTime.now(clock).minusMinutes(config.failureWindowMinutes().value());
        return failedAuthenticationRepository.countFailuresBy(ipAddress, since)
                .hasReachedTheLimit(config.maxFailures().value());
    }

    private AuthenticationBlock createNewBlockFor(IpAddress ipAddress) {
        failedAuthenticationRepository.removeAllFor(ipAddress);
        int minutes = ThreadLocalRandom.current().nextInt(
                config.minBlockMinutes().value(),
                config.maxBlockMinutes().value() + 1);
        LocalDateTime until = LocalDateTime.now(clock).plusMinutes(minutes);
        return authenticationBlockRepository.create(new AuthenticationBlock(ipAddress, until));
    }
}

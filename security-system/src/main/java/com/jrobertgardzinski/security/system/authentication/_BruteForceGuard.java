package com.jrobertgardzinski.security.system.authentication;

import com.jrobertgardzinski.security.config.bruteforce.BruteForceConfig;
import com.jrobertgardzinski.security.domain.entity.AuthenticationBlock;
import com.jrobertgardzinski.security.domain.event.BruteForceProtectionEvent;
import com.jrobertgardzinski.security.domain.repository.AuthenticationBlockRepository;
import com.jrobertgardzinski.security.domain.repository.RejectedAuthenticationRepository;
import com.jrobertgardzinski.security.domain.vo.IpAddress;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

class _BruteForceGuard {

    private final RejectedAuthenticationRepository rejectedAuthenticationRepository;
    private final AuthenticationBlockRepository authenticationBlockRepository;
    private final Clock clock;
    private final BruteForceConfig config;
    private final BlockDurationPolicy blockDurationPolicy;

    public _BruteForceGuard(RejectedAuthenticationRepository rejectedAuthenticationRepository,
                            AuthenticationBlockRepository authenticationBlockRepository,
                            Clock clock, BruteForceConfig config,
                            BlockDurationPolicy blockDurationPolicy) {
        this.rejectedAuthenticationRepository = rejectedAuthenticationRepository;
        this.authenticationBlockRepository = authenticationBlockRepository;
        this.clock = clock;
        this.config = config;
        this.blockDurationPolicy = blockDurationPolicy;
    }

    public BruteForceProtectionEvent execute(IpAddress ipAddress) {
        return existingActiveBlockFor(ipAddress)
                .<BruteForceProtectionEvent>map(BruteForceProtectionEvent.Blocked::new)
                .orElseGet(() -> failureLimitReachedFor(ipAddress)
                        ? new BruteForceProtectionEvent.Blocked(createNewBlockFor(ipAddress))
                        : new BruteForceProtectionEvent.Allowed());
    }

    private Optional<AuthenticationBlock> existingActiveBlockFor(IpAddress ipAddress) {
        return authenticationBlockRepository.findBy(ipAddress)
                .filter(block -> block.isStillActive(clock));
    }

    private boolean failureLimitReachedFor(IpAddress ipAddress) {
        LocalDateTime since = LocalDateTime.now(clock).minusMinutes(config.failureWindowMinutes().value());
        return rejectedAuthenticationRepository.countFailuresBy(ipAddress, since)
                .hasReachedTheLimit(config.maxFailures().value());
    }

    private AuthenticationBlock createNewBlockFor(IpAddress ipAddress) {
        rejectedAuthenticationRepository.removeAllFor(ipAddress);
        LocalDateTime until = LocalDateTime.now(clock).plusMinutes(blockDurationPolicy.blockMinutes());
        return authenticationBlockRepository.create(new AuthenticationBlock(ipAddress, until));
    }
}

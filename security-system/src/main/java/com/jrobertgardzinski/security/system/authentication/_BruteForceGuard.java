package com.jrobertgardzinski.security.system.authentication;

import com.jrobertgardzinski.security.config.bruteforce.BruteForceConfig;
import com.jrobertgardzinski.security.domain.entity.AuthenticationBlock;
import com.jrobertgardzinski.security.domain.event.BruteForceProtectionEvent;
import com.jrobertgardzinski.security.domain.repository.AuthenticationBlockRepository;
import com.jrobertgardzinski.security.domain.repository.RejectedAuthenticationRepository;
import com.jrobertgardzinski.security.domain.vo.Source;

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

    public BruteForceProtectionEvent execute(Source source) {
        return existingActiveBlockFor(source)
                .<BruteForceProtectionEvent>map(BruteForceProtectionEvent.Blocked::new)
                .orElseGet(() -> failureLimitReachedFor(source)
                        ? new BruteForceProtectionEvent.Blocked(createNewBlockFor(source))
                        : new BruteForceProtectionEvent.Allowed());
    }

    private Optional<AuthenticationBlock> existingActiveBlockFor(Source source) {
        return authenticationBlockRepository.findBy(source)
                .filter(block -> block.isStillActive(clock));
    }

    private boolean failureLimitReachedFor(Source source) {
        LocalDateTime since = LocalDateTime.now(clock).minusMinutes(config.failureWindowMinutes().value());
        return rejectedAuthenticationRepository.countFailuresBy(source, since)
                .hasReachedTheLimit(config.maxFailures().value());
    }

    private AuthenticationBlock createNewBlockFor(Source source) {
        rejectedAuthenticationRepository.removeAllFor(source);
        LocalDateTime until = LocalDateTime.now(clock).plusMinutes(blockDurationPolicy.blockMinutes());
        return authenticationBlockRepository.create(new AuthenticationBlock(source, until));
    }
}

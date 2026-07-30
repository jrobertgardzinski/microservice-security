package com.jrobertgardzinski.security.system.authentication;

import com.jrobertgardzinski.security.config.bruteforce.BruteForceConfig;
import com.jrobertgardzinski.security.domain.entity.AuthenticationBlock;
import com.jrobertgardzinski.security.domain.event.BruteForceProtectionEvent;
import com.jrobertgardzinski.security.domain.repository.AuthenticationBlockRepository;
import com.jrobertgardzinski.security.domain.repository.RejectedAuthenticationRepository;
import com.jrobertgardzinski.security.domain.vo.LockoutSubject;
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

    /**
     * Two counts, one window. The tight one asks "how often has THIS address missed on THIS
     * account" — the shape of guessing one person's password. The ceiling asks "how often has this
     * address missed at all" — the shape of spraying a few guesses over many accounts, which the
     * tight count alone would never see, because no single account ever reaches its limit.
     *
     * <p>Either way the BLOCK is placed on the source, never on the account: a block that followed
     * the account would let anyone lock a victim out on demand, from anywhere.
     */
    public BruteForceProtectionEvent execute(LockoutSubject subject) {
        Source source = subject.source();
        return existingActiveBlockFor(source)
                .<BruteForceProtectionEvent>map(BruteForceProtectionEvent.Blocked::new)
                .orElseGet(() -> failureLimitReachedFor(subject) || sourceCeilingReachedFor(source)
                        ? new BruteForceProtectionEvent.Blocked(createNewBlockFor(subject))
                        : new BruteForceProtectionEvent.Allowed());
    }

    private Optional<AuthenticationBlock> existingActiveBlockFor(Source source) {
        return authenticationBlockRepository.findBy(source)
                .filter(block -> block.isStillActive(clock));
    }

    private boolean failureLimitReachedFor(LockoutSubject subject) {
        return rejectedAuthenticationRepository.countFailuresOnAccount(subject, windowStart())
                .hasReachedTheLimit(config.maxFailures().value());
    }

    private boolean sourceCeilingReachedFor(Source source) {
        return rejectedAuthenticationRepository.countFailuresFromSource(source, windowStart())
                .hasReachedTheLimit(config.maxFailuresPerSource().value());
    }

    private LocalDateTime windowStart() {
        return LocalDateTime.now(clock).minusMinutes(config.failureWindowMinutes().value());
    }

    private AuthenticationBlock createNewBlockFor(LockoutSubject subject) {
        // clear only what this block now stands for; the rest of the address's record is somebody
        // else's business and must survive, or the block itself becomes the amnesty
        rejectedAuthenticationRepository.removeAllFor(subject);
        LocalDateTime until = LocalDateTime.now(clock).plusMinutes(blockDurationPolicy.blockMinutes());
        return authenticationBlockRepository.create(new AuthenticationBlock(subject.source(), until));
    }
}

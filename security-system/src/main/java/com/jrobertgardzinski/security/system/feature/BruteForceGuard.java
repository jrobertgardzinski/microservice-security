package com.jrobertgardzinski.security.system.feature;

import com.jrobertgardzinski.security.config.BruteForceConfig;
import com.jrobertgardzinski.security.domain.entity.AuthenticationBlock;
import com.jrobertgardzinski.security.domain.event.brute.force.protection.Blocked;
import com.jrobertgardzinski.security.domain.event.brute.force.protection.BruteForceProtectionEvent;
import com.jrobertgardzinski.security.domain.event.brute.force.protection.Passed;
import com.jrobertgardzinski.security.domain.repository.AuthenticationBlockRepository;
import com.jrobertgardzinski.security.domain.repository.FailedAuthenticationRepository;
import com.jrobertgardzinski.security.domain.vo.FailuresCount;
import com.jrobertgardzinski.security.domain.vo.IpAddress;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

public class BruteForceGuard implements Function<IpAddress, BruteForceProtectionEvent> {

    private final FailedAuthenticationRepository failedAuthenticationRepository;
    private final AuthenticationBlockRepository authenticationBlockRepository;
    private final Clock clock;
    private final BruteForceConfig config;

    public BruteForceGuard(FailedAuthenticationRepository failedAuthenticationRepository,
                           AuthenticationBlockRepository authenticationBlockRepository,
                           Clock clock, BruteForceConfig config) {
        this.failedAuthenticationRepository = failedAuthenticationRepository;
        this.authenticationBlockRepository = authenticationBlockRepository;
        this.clock = clock;
        this.config = config;
    }

    @Override
    public BruteForceProtectionEvent apply(IpAddress ipAddress) {
        Optional<AuthenticationBlock> optionalAuthenticationBlock = authenticationBlockRepository.findBy(ipAddress)
                .filter(block -> block.isStillActive(clock));
        if (optionalAuthenticationBlock.isPresent()) {
            AuthenticationBlock authenticationBlock = optionalAuthenticationBlock.get();
            return new Blocked(authenticationBlock);
        }
        LocalDateTime since = LocalDateTime.now(clock).minusMinutes(config.failureWindowMinutes());
        FailuresCount failuresCount = failedAuthenticationRepository.countFailuresBy(ipAddress, since);
        if (failuresCount.hasReachedTheLimit(config.maxFailures())) {
            failedAuthenticationRepository.removeAllFor(ipAddress);
            int minutes = ThreadLocalRandom.current().nextInt(config.minBlockMinutes(), config.maxBlockMinutes() + 1);
            LocalDateTime until = LocalDateTime.now(clock).plusMinutes(minutes);
            AuthenticationBlock authenticationBlock = authenticationBlockRepository.create(
                    new AuthenticationBlock(
                            ipAddress,
                            until));
            return new Blocked(authenticationBlock);
        }
        return new Passed();
    }
}

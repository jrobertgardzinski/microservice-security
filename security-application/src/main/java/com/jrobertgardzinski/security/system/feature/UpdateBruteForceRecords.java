package com.jrobertgardzinski.security.system.feature;

import com.jrobertgardzinski.security.domain.repository.FailedAuthenticationRepository;
import com.jrobertgardzinski.security.domain.vo.FailedAuthenticationDetails;
import com.jrobertgardzinski.security.domain.vo.IpAddress;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.function.Consumer;

public class UpdateBruteForceRecords implements Consumer<IpAddress> {
    private final FailedAuthenticationRepository failedAuthenticationRepository;
    private final Clock clock;

    public UpdateBruteForceRecords(FailedAuthenticationRepository failedAuthenticationRepository, Clock clock) {
        this.failedAuthenticationRepository = failedAuthenticationRepository;
        this.clock = clock;
    }

    @Override
    public void accept(IpAddress ipAddress) {
        failedAuthenticationRepository.create(
                new FailedAuthenticationDetails(ipAddress, LocalDateTime.now(clock)));
    }
}

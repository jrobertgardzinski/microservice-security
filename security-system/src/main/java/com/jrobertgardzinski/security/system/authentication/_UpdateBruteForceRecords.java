package com.jrobertgardzinski.security.system.authentication;

import com.jrobertgardzinski.security.domain.repository.FailedAuthenticationRepository;
import com.jrobertgardzinski.security.domain.vo.FailedAuthenticationDetails;
import com.jrobertgardzinski.security.domain.vo.IpAddress;

import java.time.Clock;
import java.time.LocalDateTime;

class _UpdateBruteForceRecords {
    private final FailedAuthenticationRepository failedAuthenticationRepository;
    private final Clock clock;

    public _UpdateBruteForceRecords(FailedAuthenticationRepository failedAuthenticationRepository, Clock clock) {
        this.failedAuthenticationRepository = failedAuthenticationRepository;
        this.clock = clock;
    }

    public void execute(IpAddress ipAddress) {
        failedAuthenticationRepository.create(
                new FailedAuthenticationDetails(ipAddress, LocalDateTime.now(clock)));
    }
}

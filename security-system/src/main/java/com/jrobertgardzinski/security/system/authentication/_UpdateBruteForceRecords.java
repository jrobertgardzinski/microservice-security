package com.jrobertgardzinski.security.system.authentication;

import com.jrobertgardzinski.security.domain.repository.RejectedAuthenticationRepository;
import com.jrobertgardzinski.security.domain.vo.RejectedAuthenticationDetails;
import com.jrobertgardzinski.security.domain.vo.IpAddress;

import java.time.Clock;
import java.time.LocalDateTime;

class _UpdateBruteForceRecords {
    private final RejectedAuthenticationRepository rejectedAuthenticationRepository;
    private final Clock clock;

    public _UpdateBruteForceRecords(RejectedAuthenticationRepository rejectedAuthenticationRepository, Clock clock) {
        this.rejectedAuthenticationRepository = rejectedAuthenticationRepository;
        this.clock = clock;
    }

    public void execute(IpAddress ipAddress) {
        rejectedAuthenticationRepository.create(
                new RejectedAuthenticationDetails(ipAddress, LocalDateTime.now(clock)));
    }
}

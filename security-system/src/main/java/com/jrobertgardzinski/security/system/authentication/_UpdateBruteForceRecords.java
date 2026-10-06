package com.jrobertgardzinski.security.system.authentication;

import com.jrobertgardzinski.security.domain.authentication.RejectedAuthenticationRepository;
import com.jrobertgardzinski.security.domain.core.LockoutSubject;
import com.jrobertgardzinski.security.domain.authentication.RejectedAuthenticationDetails;

import java.time.Clock;
import java.time.LocalDateTime;

/** Writes one failed attempt down, charged to the pair that made it: this source, this account. */
class _UpdateBruteForceRecords {
    private final RejectedAuthenticationRepository rejectedAuthenticationRepository;
    private final Clock clock;

    public _UpdateBruteForceRecords(RejectedAuthenticationRepository rejectedAuthenticationRepository, Clock clock) {
        this.rejectedAuthenticationRepository = rejectedAuthenticationRepository;
        this.clock = clock;
    }

    public void execute(LockoutSubject subject) {
        rejectedAuthenticationRepository.create(
                new RejectedAuthenticationDetails(subject, LocalDateTime.now(clock)));
    }
}

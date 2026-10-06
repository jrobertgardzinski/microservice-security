package com.jrobertgardzinski.security.system.authentication;

import com.jrobertgardzinski.security.domain.core.FailedSignIns;
import com.jrobertgardzinski.security.domain.authentication.RejectedAuthenticationRepository;
import com.jrobertgardzinski.security.domain.core.LockoutSubject;
import com.jrobertgardzinski.security.domain.authentication.RejectedAuthenticationDetails;

import java.time.Clock;
import java.time.LocalDateTime;

/** Writes one failed attempt down, charged to the pair that made it: this source, this account. */
public final class RecordFailedSignIn implements FailedSignIns {
    private final RejectedAuthenticationRepository rejectedAuthenticationRepository;
    private final Clock clock;

    public RecordFailedSignIn(RejectedAuthenticationRepository rejectedAuthenticationRepository, Clock clock) {
        this.rejectedAuthenticationRepository = rejectedAuthenticationRepository;
        this.clock = clock;
    }

    @Override
    public void record(LockoutSubject subject) {
        rejectedAuthenticationRepository.create(
                new RejectedAuthenticationDetails(subject, LocalDateTime.now(clock)));
    }
}

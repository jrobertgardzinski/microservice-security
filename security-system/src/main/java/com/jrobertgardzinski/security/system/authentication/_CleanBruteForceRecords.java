package com.jrobertgardzinski.security.system.authentication;

import com.jrobertgardzinski.security.domain.repository.AuthenticationBlockRepository;
import com.jrobertgardzinski.security.domain.repository.RejectedAuthenticationRepository;
import com.jrobertgardzinski.security.domain.vo.IpAddress;

class _CleanBruteForceRecords {

    private final RejectedAuthenticationRepository rejectedAuthenticationRepository;
    private final AuthenticationBlockRepository authenticationBlockRepository;

    public _CleanBruteForceRecords(RejectedAuthenticationRepository rejectedAuthenticationRepository, AuthenticationBlockRepository authenticationBlockRepository) {
        this.rejectedAuthenticationRepository = rejectedAuthenticationRepository;
        this.authenticationBlockRepository = authenticationBlockRepository;
    }

    public void execute(IpAddress ipAddress) {
        rejectedAuthenticationRepository.removeAllFor(ipAddress);
        authenticationBlockRepository.removeAllFor(ipAddress);
    }
}

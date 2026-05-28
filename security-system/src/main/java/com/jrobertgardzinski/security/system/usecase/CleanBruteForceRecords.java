package com.jrobertgardzinski.security.system.usecase;

import com.jrobertgardzinski.security.domain.repository.AuthenticationBlockRepository;
import com.jrobertgardzinski.security.domain.repository.FailedAuthenticationRepository;
import com.jrobertgardzinski.security.domain.vo.IpAddress;

public class CleanBruteForceRecords {

    private final FailedAuthenticationRepository failedAuthenticationRepository;
    private final AuthenticationBlockRepository authenticationBlockRepository;

    public CleanBruteForceRecords(FailedAuthenticationRepository failedAuthenticationRepository, AuthenticationBlockRepository authenticationBlockRepository) {
        this.failedAuthenticationRepository = failedAuthenticationRepository;
        this.authenticationBlockRepository = authenticationBlockRepository;
    }

    public void execute(IpAddress ipAddress) {
        failedAuthenticationRepository.removeAllFor(ipAddress);
        authenticationBlockRepository.removeAllFor(ipAddress);
    }
}

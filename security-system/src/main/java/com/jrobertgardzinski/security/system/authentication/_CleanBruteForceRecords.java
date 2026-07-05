package com.jrobertgardzinski.security.system.authentication;

import com.jrobertgardzinski.security.domain.repository.AuthenticationBlockRepository;
import com.jrobertgardzinski.security.domain.repository.RejectedAuthenticationRepository;
import com.jrobertgardzinski.security.domain.vo.Source;

class _CleanBruteForceRecords {

    private final RejectedAuthenticationRepository rejectedAuthenticationRepository;
    private final AuthenticationBlockRepository authenticationBlockRepository;

    public _CleanBruteForceRecords(RejectedAuthenticationRepository rejectedAuthenticationRepository, AuthenticationBlockRepository authenticationBlockRepository) {
        this.rejectedAuthenticationRepository = rejectedAuthenticationRepository;
        this.authenticationBlockRepository = authenticationBlockRepository;
    }

    public void execute(Source source) {
        rejectedAuthenticationRepository.removeAllFor(source);
        authenticationBlockRepository.removeAllFor(source);
    }
}

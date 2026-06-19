package com.jrobertgardzinski.security.system.authentication;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.password.domain.HashAlgorithmPort;
import com.jrobertgardzinski.security.domain.event.AuthenticationEvent;
import com.jrobertgardzinski.security.domain.repository.UserRepository;
import com.jrobertgardzinski.security.domain.vo.Credentials;

class _VerifyCredentials {
    private final UserRepository userRepository;
    private final HashAlgorithmPort hashAlgorithmPort;

    public _VerifyCredentials(UserRepository userRepository, HashAlgorithmPort hashAlgorithmPort) {
        this.userRepository = userRepository;
        this.hashAlgorithmPort = hashAlgorithmPort;
    }

    public AuthenticationEvent execute(Credentials credentials) {
        Email email = credentials.email();
        return userRepository.findBy(email)
                .filter(user -> hashAlgorithmPort.verify(user.passwordHash(), credentials.plaintextPassword()))
                .<AuthenticationEvent>map(_ -> new AuthenticationEvent.Valid(email))
                .orElseGet(() -> new AuthenticationEvent.Invalid(email));
    }
}

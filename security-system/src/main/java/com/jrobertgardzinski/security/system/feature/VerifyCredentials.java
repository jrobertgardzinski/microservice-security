package com.jrobertgardzinski.security.system.feature;

import com.jrobertgardzinski.password.domain.HashAlgorithmPort;
import com.jrobertgardzinski.security.domain.event.authentication.AuthenticationEvent;
import com.jrobertgardzinski.security.domain.event.authentication.AuthenticationFailedEvent;
import com.jrobertgardzinski.security.domain.event.authentication.AuthenticationPassedEvent;
import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.repository.UserRepository;
import com.jrobertgardzinski.security.domain.vo.Credentials;

public class VerifyCredentials {
    private final UserRepository userRepository;
    private final HashAlgorithmPort hashAlgorithmPort;

    public VerifyCredentials(UserRepository userRepository, HashAlgorithmPort hashAlgorithmPort) {
        this.userRepository = userRepository;
        this.hashAlgorithmPort = hashAlgorithmPort;
    }

    public AuthenticationEvent execute(Credentials credentials) {
        Email email = credentials.email();
        return userRepository.findBy(email)
                .filter(user -> hashAlgorithmPort.verify(user.passwordHash(), credentials.plaintextPassword()))
                .<AuthenticationEvent>map(_ -> new AuthenticationPassedEvent(email))
                .orElseGet(() -> new AuthenticationFailedEvent(email));
    }
}

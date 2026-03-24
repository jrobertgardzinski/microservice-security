package com.jrobertgardzinski.security.system.feature;

import com.jrobertgardzinski.password.domain.HashAlgorithmPort;
import com.jrobertgardzinski.password.domain.HashedPassword;
import com.jrobertgardzinski.password.domain.PlaintextPassword;
import com.jrobertgardzinski.security.domain.entity.User;
import com.jrobertgardzinski.security.domain.event.authentication.AuthenticationEvent;
import com.jrobertgardzinski.security.domain.event.authentication.AuthenticationFailedEvent;
import com.jrobertgardzinski.security.domain.event.authentication.AuthenticationPassedEvent;
import com.jrobertgardzinski.security.domain.repository.UserRepository;
import com.jrobertgardzinski.security.domain.vo.Credentials;
import com.jrobertgardzinski.security.domain.vo.Email;

import java.util.Optional;
import java.util.function.Function;

public class VerifyCredentials implements Function<Credentials, AuthenticationEvent> {
    private final UserRepository userRepository;
    private final HashAlgorithmPort hashAlgorithmPort;

    public VerifyCredentials(UserRepository userRepository, HashAlgorithmPort hashAlgorithmPort) {
        this.userRepository = userRepository;
        this.hashAlgorithmPort = hashAlgorithmPort;
    }

    @Override
    public AuthenticationEvent apply(Credentials credentials) {
        Email email = credentials.email();

        Optional<User> optionalUser = userRepository.findBy(email);
        if (optionalUser.isEmpty()) {
            return new AuthenticationFailedEvent(email);
        }

        HashedPassword passwordHash = optionalUser.get().passwordHash();
        PlaintextPassword enteredPassword = credentials.plaintextPassword();
        if (!hashAlgorithmPort.verify(passwordHash, enteredPassword)) {
            return new AuthenticationFailedEvent(email);
        }

        return new AuthenticationPassedEvent(email);
    }
}

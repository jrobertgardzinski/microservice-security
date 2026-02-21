package com.jrobertgardzinski.security.system.feature;

import com.jrobertgardzinski.security.domain.vo.*;
import com.jrobertgardzinski.password.domain.HashAlgorithmPort;
import com.jrobertgardzinski.password.domain.PasswordHash;
import com.jrobertgardzinski.password.domain.PlaintextPassword;
import com.jrobertgardzinski.salt.config.SaltConfig;
import com.jrobertgardzinski.salt.domain.Salt;
import com.jrobertgardzinski.security.domain.entity.User;
import com.jrobertgardzinski.security.domain.event.registration.PossibleRaceCondition;
import com.jrobertgardzinski.security.domain.event.registration.RegistrationEvent;
import com.jrobertgardzinski.security.domain.event.registration.RegistrationPassedEvent;
import com.jrobertgardzinski.security.domain.event.registration.UserAlreadyExistsEvent;
import com.jrobertgardzinski.security.domain.repository.UserRepository;

import java.util.function.Function;

public class Register implements Function<UserRegistration, RegistrationEvent> {
    private final UserRepository userRepository;
    private final HashAlgorithmPort hashAlgorithmPort;
    private final SaltConfig saltConfig;

    public Register(UserRepository userRepository, HashAlgorithmPort hashAlgorithmPort, SaltConfig saltConfig) {
        this.userRepository = userRepository;
        this.hashAlgorithmPort = hashAlgorithmPort;
        this.saltConfig = saltConfig;
    }

    @Override
    public RegistrationEvent apply(UserRegistration userRegistration) {
        Email email = userRegistration.email();
        if (userRepository.existsBy(email)) {
            return new UserAlreadyExistsEvent();
        }
        try {
            PlaintextPassword plaintextPassword = userRegistration.plaintextPassword();
            Salt salt = Salt.generate(saltConfig.byteLength());
            PasswordHash passwordHash = hashAlgorithmPort.hash(plaintextPassword, salt);
            User user = new User(
                    userRegistration.email(),
                    passwordHash
            );
            userRepository.save(user);
            return new RegistrationPassedEvent(email);
        } catch (Exception e) {
            return new PossibleRaceCondition();
        }
    }
}

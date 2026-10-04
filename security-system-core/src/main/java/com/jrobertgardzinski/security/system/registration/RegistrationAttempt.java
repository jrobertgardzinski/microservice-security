package com.jrobertgardzinski.security.system.registration;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.email.domain.NormalizedEmail;
import com.jrobertgardzinski.password.domain.HashedPassword;
import com.jrobertgardzinski.security.domain.entity.User;
import com.jrobertgardzinski.security.domain.repository.EmailAlreadyTakenException;
import com.jrobertgardzinski.security.domain.repository.UserRepository;

class RegistrationAttempt {

    private final _EmailVerdict email;
    private final _PasswordVerdict password;
    private final UserRepository userRepository;

    RegistrationAttempt(_EmailVerdict email, _PasswordVerdict password, UserRepository userRepository) {
        this.email = email;
        this.password = password;
        this.userRepository = userRepository;
    }

    RegisterResult resolve() {
        var acceptedEmail = email.accepted();
        var acceptedHash = password.accepted();
        if (acceptedEmail.isEmpty() || acceptedHash.isEmpty()) {
            return new RegisterResult.Rejected(
                    email.errorCodes(), email.policy(),
                    password.errorCodes(), password.policy());
        }
        Email address = acceptedEmail.get();
        HashedPassword hashedPassword = acceptedHash.get();

        if (userRepository.existsBy(NormalizedEmail.of(address))) {
            return new RegisterResult.EmailAlreadyTaken(address);
        }

        User user = new User(address, hashedPassword);
        try {
            return new RegisterResult.Registered(userRepository.save(user));
        } catch (EmailAlreadyTakenException e) {
            // the storage uniqueness check lost the race after our existsBy check passed
            return new RegisterResult.EmailAlreadyTaken(address);
        }
    }
}

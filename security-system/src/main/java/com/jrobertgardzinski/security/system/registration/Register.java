package com.jrobertgardzinski.security.system.registration;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.email.policy.CanRegister;
import com.jrobertgardzinski.password.domain.HashAlgorithmPort;
import com.jrobertgardzinski.password.domain.PlaintextPassword;
import com.jrobertgardzinski.password.policy.CreatePasswordHash;
import com.jrobertgardzinski.password.policy.PasswordPolicy;
import com.jrobertgardzinski.security.domain.repository.UserRepository;

import java.util.function.Supplier;

/**
 * Registers a new user from an email and a plaintext password: the email must
 * be allowed to register and not already taken, and the password is hashed
 * before the user is stored. The outcome is reported as a {@link RegisterResult}.
 */
public class Register {
    private final UserRepository userRepository;
    private final CanRegister canRegister;
    private final HashAlgorithmPort hashAlgorithm;
    private final Supplier<PasswordPolicy> passwordPolicy;

    public Register(UserRepository userRepository, CanRegister canRegister,
                    HashAlgorithmPort hashAlgorithm, Supplier<PasswordPolicy> passwordPolicy) {
        this.userRepository = userRepository;
        this.canRegister = canRegister;
        this.hashAlgorithm = hashAlgorithm;
        this.passwordPolicy = passwordPolicy;
    }

    public RegisterResult execute(Supplier<Email> email, Supplier<PlaintextPassword> password) {
        RegistrationAttempt attempt = new RegistrationAttempt(
                canRegister.evaluate(email),
                new CreatePasswordHash(hashAlgorithm, passwordPolicy.get()).create(password),
                userRepository
        );
        return attempt.resolve();
    }
}

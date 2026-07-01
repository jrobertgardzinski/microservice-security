package com.jrobertgardzinski.security.system.registration;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.email.policy.CanRegister;
import com.jrobertgardzinski.password.domain.PlaintextPassword;
import com.jrobertgardzinski.password.policy.CreatePasswordHash;
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
    private final CreatePasswordHash createPasswordHash;

    public Register(UserRepository userRepository, CanRegister canRegister, CreatePasswordHash createPasswordHash) {
        this.userRepository = userRepository;
        this.canRegister = canRegister;
        this.createPasswordHash = createPasswordHash;
    }

    public RegisterResult execute(Supplier<Email> email, Supplier<PlaintextPassword> password) {
        RegistrationAttempt attempt = new RegistrationAttempt(
                canRegister.evaluate(email),
                createPasswordHash.create(password),
                userRepository
        );
        return attempt.resolve();
    }
}

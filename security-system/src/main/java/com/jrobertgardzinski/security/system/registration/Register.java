package com.jrobertgardzinski.security.system.registration;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.email.policy.CanRegister;
import com.jrobertgardzinski.password.domain.HashedPassword;
import com.jrobertgardzinski.password.domain.PlaintextPassword;
import com.jrobertgardzinski.password.policy.CreatePasswordHash;
import com.jrobertgardzinski.security.domain.entity.User;
import com.jrobertgardzinski.security.domain.repository.UserRepository;
import com.jrobertgardzinski.util.constraint.Outcome;

import java.util.List;
import java.util.function.Supplier;

public class Register {
    private final UserRepository userRepository;
    private final CanRegister canRegister;
    private final CreatePasswordHash createPasswordHash;

    public Register(UserRepository userRepository, CanRegister canRegister, CreatePasswordHash createPasswordHash) {
        this.userRepository = userRepository;
        this.canRegister = canRegister;
        this.createPasswordHash = createPasswordHash;
    }

    // TODO it surely may be improved! First, check for errors, then operate on outcomes reducing the risk of using suppliers elsewhere than for creating outcomes!
    public RegisterResult execute(Supplier<Email> email, Supplier<PlaintextPassword> password) {
        Outcome<Email> emailOutcome = canRegister.evaluate(email);
        List<String> emailErrors = emailOutcome.errorCodes();

        Outcome<HashedPassword> passwordOutcome = createPasswordHash.create(password);
        List<String> passwordErrors = passwordOutcome.errorCodes();

        if (!emailErrors.isEmpty() || !passwordErrors.isEmpty()) {
            return new RegisterResult.Rejected(emailErrors, passwordErrors);
        }

        Email validatedEmail = emailOutcome.findValue().get();
        HashedPassword hashedPassword = passwordOutcome.findValue().get();

        User userToSave = new User(validatedEmail, hashedPassword);
        User savedUser = userRepository.save(userToSave);
        return new RegisterResult.Registered(savedUser);
    }
}

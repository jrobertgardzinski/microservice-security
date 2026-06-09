package com.jrobertgardzinski.security.system.registration;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.email.policy.CanRegister;
import com.jrobertgardzinski.password.domain.HashedPassword;
import com.jrobertgardzinski.password.domain.PlaintextPassword;
import com.jrobertgardzinski.password.policy.CreatePasswordHash;
import com.jrobertgardzinski.security.domain.entity.User;
import com.jrobertgardzinski.security.domain.repository.UserRepository;
import com.jrobertgardzinski.util.constraint.Decision;
import com.jrobertgardzinski.util.constraint.Outcome;

import java.util.List;

public class Register {
    private final UserRepository userRepository;
    private final CanRegister canRegister;
    private final CreatePasswordHash createPasswordHash;

    public Register(UserRepository userRepository, CanRegister canRegister, CreatePasswordHash createPasswordHash) {
        this.userRepository = userRepository;
        this.canRegister = canRegister;
        this.createPasswordHash = createPasswordHash;
    }

    public RegisterResult execute(Email email, PlaintextPassword password) {
        Decision<Email> emailDecision = canRegister.evaluate(email);
        List<String> emailErrors = emailDecision.errorCodes();

        Outcome<HashedPassword> passwordOutcome = createPasswordHash.create(password);
        List<String> passwordErrors = passwordOutcome.errorCodes();

        return !emailErrors.isEmpty() || !passwordErrors.isEmpty() ?
            new RegisterResult.Invalid(emailErrors, passwordErrors) :
            passwordOutcome.findValue()
                .map(hash -> {
                    User userToSave = new User(email, hash);
                    User savedUser = userRepository.save(userToSave);
                    return new RegisterResult.Valid(savedUser);
                })
                .orElseThrow();
    }
}

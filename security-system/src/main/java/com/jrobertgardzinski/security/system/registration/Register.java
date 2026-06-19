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

    public RegisterResult execute(Supplier<Email> email, Supplier<PlaintextPassword> password) {
        Decision<Email> emailDecision = canRegister.evaluate(email);
        List<String> emailErrors = emailDecision.errorCodes();

        Outcome<HashedPassword> passwordOutcome = createPasswordHash.create(password);
        List<String> passwordErrors = passwordOutcome.errorCodes();

        return !emailErrors.isEmpty() || !passwordErrors.isEmpty() ?
                new RegisterResult.Rejected(emailErrors, passwordErrors) :
                passwordOutcome.findValue()
                        .map(hash -> {
                            User userToSave = new User(email.get(), hash);
                            User savedUser = userRepository.save(userToSave);
                            return new RegisterResult.Registered(savedUser);
                        })
                        .orElseThrow();
    }


/*    public RegisterResult execute(String rawEmail, String rawPassword) {
        Email email = null;
        List<String> emailErrors;
        try {
            email = Email.of(rawEmail);
            emailErrors = canRegister.evaluate(email).errorCodes();
        } catch (IllegalArgumentException e) {
            emailErrors = List.of(e.getMessage());
        }

        HashedPassword hash = null;
        List<String> passwordErrors;
        try {
            Outcome<HashedPassword> outcome = createPasswordHash.create(PlaintextPassword.of(rawPassword));
            passwordErrors = outcome.errorCodes();
            hash = outcome.findValue().orElse(null);
        } catch (IllegalArgumentException e) {
            passwordErrors = List.of(e.getMessage());
        }

        if (!emailErrors.isEmpty() || !passwordErrors.isEmpty()) {
            return new RegisterResult.Rejected(emailErrors, passwordErrors);
        }
        return new RegisterResult.Registered(userRepository.save(new User(email, hash)));
    }*/
}

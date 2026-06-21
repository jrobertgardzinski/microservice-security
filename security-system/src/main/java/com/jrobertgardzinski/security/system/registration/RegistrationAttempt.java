package com.jrobertgardzinski.security.system.registration;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.password.domain.HashedPassword;
import com.jrobertgardzinski.security.domain.entity.User;
import com.jrobertgardzinski.security.domain.repository.UserRepository;
import com.jrobertgardzinski.util.constraint.Outcome;

/**
 * One registration attempt: the email and password outcomes plus the repository
 * needed to persist a successful one.
 *
 * The input suppliers were already consumed into these outcomes before this
 * object existed, so {@link #resolve} cannot re-run them. It rejects with the
 * collected error codes if either input failed; otherwise, once the email is
 * confirmed free, it builds and persists the user from the validated values.
 */
class RegistrationAttempt {

    private final Outcome<Email> emailOutcome;
    private final Outcome<HashedPassword> passwordOutcome;
    private final UserRepository userRepository;

    RegistrationAttempt(Outcome<Email> emailOutcome, Outcome<HashedPassword> passwordOutcome, UserRepository userRepository) {
        this.emailOutcome = emailOutcome;
        this.passwordOutcome = passwordOutcome;
        this.userRepository = userRepository;
    }

    RegisterResult resolve() {
        var optionalEmail = emailOutcome.findValue();
        var optionalHashedPassword = passwordOutcome.findValue();
        if (optionalEmail.isEmpty() || optionalHashedPassword.isEmpty()) {
            return new RegisterResult.Rejected(EmailErrorCodes.of(emailOutcome), PasswordErrorCodes.of(passwordOutcome));
        }
        Email email = optionalEmail.get();
        HashedPassword hashedPassword = optionalHashedPassword.get();

        if (userRepository.findBy(email).isPresent()) {
            return new RegisterResult.EmailAlreadyTaken(email);
        }

        User user = new User(email, hashedPassword);
        User persisted = userRepository.save(user);
        return new RegisterResult.Registered(persisted);
    }
}

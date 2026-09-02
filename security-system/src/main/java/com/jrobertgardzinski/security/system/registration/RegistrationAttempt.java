package com.jrobertgardzinski.security.system.registration;

import com.jrobertgardzinski.email.config.CanRegisterConfig;
import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.email.domain.NormalizedEmail;
import com.jrobertgardzinski.password.domain.HashedPassword;
import com.jrobertgardzinski.password.policy.PasswordPolicy;
import com.jrobertgardzinski.security.domain.entity.User;
import com.jrobertgardzinski.security.domain.repository.EmailAlreadyTakenException;
import com.jrobertgardzinski.security.domain.repository.UserRepository;
import com.jrobertgardzinski.util.constraint.Outcome;

/**
 * One registration attempt: the email and password outcomes, the two policies they
 * were measured against, plus the repository needed to persist a successful one.
 *
 * The input suppliers were already consumed into these outcomes before this
 * object existed, so {@link #resolve} cannot re-run them. It rejects with the
 * collected error codes if either input failed; otherwise, once the email is
 * confirmed free, it builds and persists the user from the validated values.
 */
class RegistrationAttempt {

    private final Outcome<Email> emailOutcome;
    private final CanRegisterConfig emailPolicy;
    private final Outcome<HashedPassword> passwordOutcome;
    private final PasswordPolicy passwordPolicy;
    private final UserRepository userRepository;

    RegistrationAttempt(Outcome<Email> emailOutcome, CanRegisterConfig emailPolicy,
                        Outcome<HashedPassword> passwordOutcome, PasswordPolicy passwordPolicy,
                        UserRepository userRepository) {
        this.emailOutcome = emailOutcome;
        this.emailPolicy = emailPolicy;
        this.passwordOutcome = passwordOutcome;
        this.passwordPolicy = passwordPolicy;
        this.userRepository = userRepository;
    }

    RegisterResult resolve() {
        var optionalEmail = emailOutcome.findValue();
        var optionalHashedPassword = passwordOutcome.findValue();
        if (optionalEmail.isEmpty() || optionalHashedPassword.isEmpty()) {
            return new RegisterResult.Rejected(
                    EmailErrorCodes.of(emailOutcome), emailPolicy,
                    PasswordErrorCodes.of(passwordOutcome), passwordPolicy);
        }
        Email email = optionalEmail.get();
        HashedPassword hashedPassword = optionalHashedPassword.get();

        if (userRepository.existsBy(NormalizedEmail.of(email))) {
            return new RegisterResult.EmailAlreadyTaken(email);
        }

        User user = new User(email, hashedPassword);
        try {
            return new RegisterResult.Registered(userRepository.save(user));
        } catch (EmailAlreadyTakenException e) {
            // the storage uniqueness check lost the race after our existsBy check passed
            return new RegisterResult.EmailAlreadyTaken(email);
        }
    }
}

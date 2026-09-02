package com.jrobertgardzinski.security.system.registration;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.email.domain.NormalizedEmail;
import com.jrobertgardzinski.password.domain.HashedPassword;
import com.jrobertgardzinski.security.domain.entity.User;
import com.jrobertgardzinski.security.domain.repository.EmailAlreadyTakenException;
import com.jrobertgardzinski.security.domain.repository.UserRepository;

/**
 * One registration attempt: the two verdicts — each an outcome with the policy it was measured
 * against — plus the repository needed to persist a successful one.
 *
 * The input suppliers were already consumed into these verdicts before this
 * object existed, so {@link #resolve} cannot re-run them. It rejects with the
 * collected error codes and both policies if either input failed; otherwise, once
 * the email is confirmed free, it builds and persists the user from the accepted values.
 */
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

package com.jrobertgardzinski.security.system.registration;

import com.jrobertgardzinski.email.config.CanRegisterConfig;
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
 *
 * Both policies are suppliers, resolved once per attempt: the email policy (blocked, disposable
 * and company domains) and the password policy are configuration, and a change to either must be
 * honoured by the next attempt — and travel with the refusal it produced.
 */
public class Register {
    private final UserRepository userRepository;
    private final Supplier<CanRegisterConfig> emailPolicy;
    private final HashAlgorithmPort hashAlgorithm;
    private final Supplier<PasswordPolicy> passwordPolicy;

    public Register(UserRepository userRepository, Supplier<CanRegisterConfig> emailPolicy,
                    HashAlgorithmPort hashAlgorithm, Supplier<PasswordPolicy> passwordPolicy) {
        this.userRepository = userRepository;
        this.emailPolicy = emailPolicy;
        this.hashAlgorithm = hashAlgorithm;
        this.passwordPolicy = passwordPolicy;
    }

    public RegisterResult execute(Supplier<Email> email, Supplier<PlaintextPassword> password) {
        CanRegisterConfig emailRules = emailPolicy.get();
        PasswordPolicy passwordRules = passwordPolicy.get();
        RegistrationAttempt attempt = new RegistrationAttempt(
                canRegister(emailRules).evaluate(email),
                emailRules,
                new CreatePasswordHash(hashAlgorithm, passwordRules).create(password),
                passwordRules,
                userRepository
        );
        return attempt.resolve();
    }

    /** The email policy in force, as the constraints it stands for; an absent list is an absent rule. */
    static CanRegister canRegister(CanRegisterConfig policy) {
        return CanRegister.builder()
                .blockingDomains(policy.blockedDomains())
                .blockingDisposable(policy.disposableDomains())
                .requiringCompanyEmployee(policy.companyDomains())
                .build();
    }
}

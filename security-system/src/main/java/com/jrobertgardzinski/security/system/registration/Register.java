package com.jrobertgardzinski.security.system.registration;

import com.jrobertgardzinski.email.config.CanRegisterConfig;
import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.password.domain.HashAlgorithmPort;
import com.jrobertgardzinski.password.domain.PlaintextPassword;
import com.jrobertgardzinski.security.domain.repository.UserRepository;
import com.jrobertgardzinski.password.policy.ladder.PasswordPolicyInForce;

import java.util.function.Supplier;

/**
 * Registers a new user from an email and a plaintext password: the email must
 * be allowed to register and not already taken, and the password is hashed
 * before the user is stored. The outcome is reported as a {@link RegisterResult}.
 *
 * The two policies differ in how they move, and the signature says so. The email policy (blocked,
 * disposable and company domains) is a deployment-rung value: fixed for the life of this object,
 * so it is held as one. The password policy can change while the system runs, so it is ASKED FOR
 * per attempt through {@link PasswordPolicyInForce}. Either way the policy that judged an attempt
 * travels with the refusal it produced.
 */
public class Register {
    private final UserRepository userRepository;
    private final CanRegisterConfig emailPolicy;
    private final HashAlgorithmPort hashAlgorithm;
    private final PasswordPolicyInForce passwordPolicy;

    public Register(UserRepository userRepository, CanRegisterConfig emailPolicy,
                    HashAlgorithmPort hashAlgorithm, PasswordPolicyInForce passwordPolicy) {
        this.userRepository = userRepository;
        this.emailPolicy = emailPolicy;
        this.hashAlgorithm = hashAlgorithm;
        this.passwordPolicy = passwordPolicy;
    }

    public RegisterResult execute(Supplier<Email> email, Supplier<PlaintextPassword> password) {
        return new RegistrationAttempt(
                _EmailVerdict.judge(emailPolicy, email),
                _PasswordVerdict.judge(hashAlgorithm, passwordPolicy.current(), password),
                userRepository
        ).resolve();
    }
}

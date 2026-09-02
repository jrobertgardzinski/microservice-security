package com.jrobertgardzinski.security.system.registration;

import com.jrobertgardzinski.email.config.CanRegisterConfig;
import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.password.domain.HashAlgorithmPort;
import com.jrobertgardzinski.password.domain.PlaintextPassword;
import com.jrobertgardzinski.security.domain.repository.UserRepository;
import com.jrobertgardzinski.password.policy.ladder.PasswordPolicyInForce;

import java.util.function.Supplier;

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

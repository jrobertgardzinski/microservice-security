package com.jrobertgardzinski.security.system.registration;

import com.jrobertgardzinski.email.config.CanRegisterConfig;
import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.email.policy.CanRegister;
import com.jrobertgardzinski.password.domain.HashAlgorithmPort;
import com.jrobertgardzinski.password.domain.PlaintextPassword;
import com.jrobertgardzinski.security.domain.repository.UserRepository;
import com.jrobertgardzinski.password.policy.PasswordPolicyInForce;
import com.jrobertgardzinski.security.domain.vo.RegisterRequest;

import java.util.function.Supplier;

public class Register {
    private final UserRepository userRepository;
    private final CanRegisterConfig canRegisterConfig;
    private final HashAlgorithmPort hashAlgorithm;
    private final PasswordPolicyInForce passwordPolicyInForce;

    public Register(UserRepository userRepository, CanRegisterConfig canRegisterConfig,
                    HashAlgorithmPort hashAlgorithm, PasswordPolicyInForce passwordPolicyInForce) {
        this.userRepository = userRepository;
        this.canRegisterConfig = canRegisterConfig;
        this.hashAlgorithm = hashAlgorithm;
        this.passwordPolicyInForce = passwordPolicyInForce;
    }

    public RegisterResult execute(Supplier<Email> email, Supplier<PlaintextPassword> password) {


        return new RegistrationAttempt(
                _EmailVerdict.judge(canRegisterConfig, email),
                _PasswordVerdict.judge(hashAlgorithm, passwordPolicyInForce.current(), password),
                userRepository
        ).resolve();
    }

    public RegisterResult execute2(RegisterRequest registerRequest) {
        Email email = registerRequest.getEmail();
        PlaintextPassword plaintextPassword = registerRequest.getPlaintextPassword();

        CanRegister canRegister = CanRegister.builder()
                .blockingDomains(canRegisterConfig.blockedDomains())
                .blockingDisposable(canRegisterConfig.disposableDomains())
                .requiringCompanyEmployee(canRegisterConfig.companyDomains())
                .build();
        // dobra chuj, dalej nie dam rady. Chciałem po prostu w tym miejscu w prosty sposób zwalidować email podany do rejestracji i wyjebać error, potem jeszcze password do walidacji i też wyjebać error albo dać sukces. Dasz rade to ogarnąć?
    }
}

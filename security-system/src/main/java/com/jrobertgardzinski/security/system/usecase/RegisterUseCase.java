package com.jrobertgardzinski.security.system.usecase;

import com.jrobertgardzinski.email.config.port.EmailConfigPort;
import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.email.external.MxRecordPort;
import com.jrobertgardzinski.email.policy.CanRegister;
import com.jrobertgardzinski.password.domain.HashAlgorithmPort;
import com.jrobertgardzinski.password.domain.PlaintextPassword;
import com.jrobertgardzinski.password.policy.CreatePasswordHash;
import com.jrobertgardzinski.password.policy.PasswordPolicy;
import com.jrobertgardzinski.security.domain.vo.UserRegistration;
import com.jrobertgardzinski.security.system.feature.Register;

public class RegisterUseCase {
    private final Register register;
    private final EmailConfigPort emailConfigPort;
    private final MxRecordPort mxRecordPort;
    private final HashAlgorithmPort hashAlgorithmPort;
    private final PasswordPolicy passwordPolicy;

    public RegisterUseCase(Register register, EmailConfigPort emailConfigPort, MxRecordPort mxRecordPort, HashAlgorithmPort hashAlgorithmPort, PasswordPolicy passwordPolicy) {
        this.register = register;
        this.emailConfigPort = emailConfigPort;
        this.mxRecordPort = mxRecordPort;
        this.hashAlgorithmPort = hashAlgorithmPort;
        this.passwordPolicy = passwordPolicy;
    }

public RegisterResult execute(Email email, PlaintextPassword password) {
        CanRegister canRegister = CanRegister.builder()
                .blockingDisposable(emailConfigPort.disposableDomains())
                .blockingDomains(emailConfigPort.blockedDomains())
                .requiringCompanyEmployee(emailConfigPort.companyDomains())
                .warningOnMissingMx(mxRecordPort)
                .build();
        CreatePasswordHash createPasswordHash = new CreatePasswordHash(hashAlgorithmPort, passwordPolicy);

        return RegisterResult.from(
                canRegister.evaluate(email),
                createPasswordHash.create(password),
                hash -> register.execute(new UserRegistration(email, hash)));
    }
}

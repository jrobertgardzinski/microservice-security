package com.jrobertgardzinski.security.domain.factory;

import com.jrobertgardzinski.security.domain.validation.password.PasswordPolicyPort;
import com.jrobertgardzinski.security.domain.vo.PlaintextPassword;

public class PlaintextPasswordFactory {
    private final PasswordPolicyPort passwordPolicyPort;

    public PlaintextPasswordFactory(PasswordPolicyPort passwordPolicyPort) {
        this.passwordPolicyPort = passwordPolicyPort;
    }

    public PlaintextPassword create(String plaintextPassword) {
        Validate<PlaintextPassword> plaintextPasswordValidate =
                new Validate<>(() ->
                        PlaintextPassword.of(plaintextPassword, passwordPolicyPort));

        if (plaintextPasswordValidate.isFailure()) {
            throw new IllegalArgumentException(
                    plaintextPasswordValidate.getExceptionMessage());
        }
        else {
            return plaintextPasswordValidate.getResult();
        }
    }
}

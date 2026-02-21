package com.jrobertgardzinski.security.application.factory;

import com.jrobertgardzinski.password.domain.PlaintextPassword;
import com.jrobertgardzinski.password.factory.PasswordFactory;
import com.jrobertgardzinski.security.domain.vo.Email;
import com.jrobertgardzinski.security.domain.vo.UserRegistration;

import java.util.Collections;
import java.util.List;

public class RegisterFactory {

    private final PasswordFactory passwordFactory;

    public RegisterFactory(PasswordFactory passwordFactory) {
        this.passwordFactory = passwordFactory;
    }

    public UserRegistration create(String email, String plaintextPassword) throws UserRegistrationValidationException {
        Validate<Email> emailValidate = new Validate<>(() -> new Email(email));
        Validate<PlaintextPassword> plaintextPasswordValidate = new Validate<>(() -> passwordFactory.create(plaintextPassword));

        List<String> emailErrors = emailValidate.isFailure()
                ? List.of(emailValidate.getExceptionMessage())
                : Collections.emptyList();

        List<String> passwordErrors = plaintextPasswordValidate.isFailure()
                ? List.of(plaintextPasswordValidate.getExceptionMessage())
                : Collections.emptyList();

        if (!emailErrors.isEmpty() || !passwordErrors.isEmpty()) {
            throw new UserRegistrationValidationException(emailErrors, passwordErrors);
        }

        return new UserRegistration(emailValidate.getResult(), plaintextPasswordValidate.getResult());
    }
}

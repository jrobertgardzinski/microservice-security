package com.jrobertgardzinski.security.application.factory;

import com.jrobertgardzinski.security.domain.factory.PlaintextPasswordFactory;
import com.jrobertgardzinski.security.domain.factory.Validate;
import com.jrobertgardzinski.security.domain.vo.Email;
import com.jrobertgardzinski.security.domain.vo.PlaintextPassword;
import com.jrobertgardzinski.security.domain.vo.UserRegistration;

import java.util.Collections;
import java.util.List;

public class RegisterFactory {

    private final PlaintextPasswordFactory plaintextPasswordFactory;

    public RegisterFactory(PlaintextPasswordFactory plaintextPasswordFactory) {
        this.plaintextPasswordFactory = plaintextPasswordFactory;
    }

    public UserRegistration create(String email, String plaintextPassword) throws UserRegistrationValidationException {
        Validate<Email> emailValidate = new Validate<>(() -> new Email(email));
        Validate<PlaintextPassword> plaintextPasswordValidate = new Validate<>(() -> plaintextPasswordFactory.create(plaintextPassword));

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

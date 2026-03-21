package com.jrobertgardzinski.security.application.factory;

import com.jrobertgardzinski.password.domain.PlaintextPassword;
import com.jrobertgardzinski.security.domain.vo.Email;
import com.jrobertgardzinski.security.domain.vo.UserRegistration;
import com.jrobertgardzinski.util.constraint.ErrorConstraint;

import java.util.Collections;
import java.util.List;

public class RegisterFactory {

    private final List<ErrorConstraint<PlaintextPassword>> passwordConstraints;

    public RegisterFactory(List<ErrorConstraint<PlaintextPassword>> passwordConstraints) {
        this.passwordConstraints = List.copyOf(passwordConstraints);
    }

    public UserRegistration create(String email, String plaintextPassword) throws UserRegistrationValidationException {
        Validate<Email> emailValidate = new Validate<>(() -> new Email(email));

        List<String> passwordErrors;
        PlaintextPassword password = null;
        try {
            PlaintextPassword p = PlaintextPassword.of(plaintextPassword);
            List<String> codes = passwordConstraints.stream()
                    .filter(c -> !c.isSatisfied(p))
                    .map(ErrorConstraint::code)
                    .toList();
            if (codes.isEmpty()) {
                password = p;
                passwordErrors = Collections.emptyList();
            } else {
                passwordErrors = codes;
            }
        } catch (IllegalArgumentException e) {
            passwordErrors = List.of(e.getMessage());
        }

        List<String> emailErrors = emailValidate.isFailure()
                ? List.of(emailValidate.getExceptionMessage())
                : Collections.emptyList();

        if (!emailErrors.isEmpty() || !passwordErrors.isEmpty()) {
            throw new UserRegistrationValidationException(emailErrors, passwordErrors);
        }

        return new UserRegistration(emailValidate.getResult(), password);
    }
}

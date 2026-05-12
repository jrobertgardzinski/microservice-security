package com.jrobertgardzinski.security.system.usecase;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.email.policy.CanRegister;
import com.jrobertgardzinski.security.system.feature.Register;

public class RegisterUseCase {
    private final Register register;
    private final RegistrationParser registrationParser;

    public RegisterUseCase(Register register, RegistrationParser registrationParser) {
        this.register = register;
        this.registrationParser = registrationParser;
    }

    public RegisterResult execute(Email email, Password password) {
        CanRegister canRegister = new CanRegister();
        return switch (registrationParser.parse(email, password)) {
            case RegistrationParser.Result.Valid v -> new RegisterResult.Valid(register.apply(v.registration()));
            case RegistrationParser.Result.Invalid i -> new RegisterResult.Invalid(i.emailErrors(), i.passwordErrors());
        };
    }
}

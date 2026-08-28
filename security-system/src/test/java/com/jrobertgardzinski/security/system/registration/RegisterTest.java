package com.jrobertgardzinski.security.system.registration;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.email.policy.CanRegister;
import com.jrobertgardzinski.password.domain.HashAlgorithmPort;
import com.jrobertgardzinski.password.domain.HashedPassword;
import com.jrobertgardzinski.password.domain.PlaintextPassword;
import com.jrobertgardzinski.password.policy.PasswordPolicy;
import com.jrobertgardzinski.security.domain.entity.User;
import com.jrobertgardzinski.security.domain.repository.UserRepository;
import com.jrobertgardzinski.util.constraint.Outcome;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeTry;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Epic("Use case")
@Feature("Register")
class RegisterTest {

    private static final String EMAIL = "user@example.com";
    private static final String PASSWORD = "StrongPassword1!";
    private static final String WEAK_PASSWORD = "weak";
    private static final HashedPassword HASH = new HashedPassword("hash:" + PASSWORD);
    private static final HashAlgorithmPort FAKE_ALGORITHM = new HashAlgorithmPort() {
        @Override
        public HashedPassword hash(PlaintextPassword plaintextPassword) {
            return new HashedPassword("hash:" + plaintextPassword.value());
        }

        @Override
        public boolean verify(HashedPassword hashedPassword, PlaintextPassword plaintextPassword) {
            return hashedPassword.value().equals("hash:" + plaintextPassword.value());
        }
    };

    private UserRepository userRepository;
    private CanRegister canRegister;
    private Register register;

    @BeforeTry
    void init() {
        userRepository = Mockito.mock(UserRepository.class);
        canRegister = Mockito.mock(CanRegister.class);
        register = new Register(userRepository, canRegister, FAKE_ALGORITHM, PasswordPolicy::withDefaults);
    }

    @Property
    @Label("Rejected when email, password, or both fail validation")
    void rejected_when_any_validation_fails(
            @ForAll boolean emailFails,
            @ForAll boolean passwordFails) {

        Assume.that(emailFails || passwordFails);

        List<String> emailErrors = emailFails ? someErrors() : Collections.emptyList();
        String password = passwordFails ? WEAK_PASSWORD : PASSWORD;

        Outcome<Email> emailOutcome = emailDecision(emailErrors);
        Mockito.when(canRegister.evaluate(Mockito.any())).thenReturn(emailOutcome);

        RegisterResult result = register.execute(() -> Email.of(EMAIL), () -> PlaintextPassword.of(password));

        RegisterResult.Rejected rejected = assertInstanceOf(RegisterResult.Rejected.class, result);
        assertAll(
                () -> assertEquals(emailErrors, rejected.emailErrors().codes()),
                () -> assertEquals(passwordFails, !rejected.passwordErrors().codes().isEmpty()),
                () -> Mockito.verify(userRepository, Mockito.never()).save(Mockito.any())
        );
    }

    @SuppressWarnings("unchecked")
    private List<String> someErrors() {
        List<String> errors = Mockito.mock(List.class);
        Mockito.when(errors.isEmpty()).thenReturn(false);
        return errors;
    }
    private Outcome<Email> emailDecision(List<String> errors) {
        return errors.isEmpty() ? new Outcome.Allowed<>(Email.of(EMAIL)) : new Outcome.Rejected<>(errors);
    }

    @Example
    @Label("Registered when both email and password pass validation")
    void registered_when_both_pass() {
        User user = new User(Email.of(EMAIL), HASH);
        Mockito.when(canRegister.evaluate(Mockito.any())).thenReturn(new Outcome.Allowed<>(Email.of(EMAIL)));
        Mockito.when(userRepository.save(Mockito.any())).thenReturn(user);

        RegisterResult result = register.execute(() -> Email.of(EMAIL), () -> PlaintextPassword.of(PASSWORD));

        RegisterResult.Registered registered = assertInstanceOf(RegisterResult.Registered.class, result);
        assertEquals(user, registered.user());
    }

    @Example
    @Label("EmailAlreadyTaken when a user with that email already exists")
    void email_already_taken_when_user_exists() {
        Mockito.when(canRegister.evaluate(Mockito.any())).thenReturn(new Outcome.Allowed<>(Email.of(EMAIL)));
        Mockito.when(userRepository.existsBy(Mockito.any())).thenReturn(true);

        RegisterResult result = register.execute(() -> Email.of(EMAIL), () -> PlaintextPassword.of(PASSWORD));

        RegisterResult.EmailAlreadyTaken alreadyTaken = assertInstanceOf(RegisterResult.EmailAlreadyTaken.class, result);
        assertAll(
                () -> assertEquals(Email.of(EMAIL), alreadyTaken.email()),
                () -> Mockito.verify(userRepository, Mockito.never()).save(Mockito.any())
        );
    }
}

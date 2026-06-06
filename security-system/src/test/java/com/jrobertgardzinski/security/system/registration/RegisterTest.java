package com.jrobertgardzinski.security.system.registration;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.email.policy.CanRegister;
import com.jrobertgardzinski.password.domain.HashedPassword;
import com.jrobertgardzinski.password.domain.PlaintextPassword;
import com.jrobertgardzinski.password.policy.CreatePasswordHash;
import com.jrobertgardzinski.security.domain.entity.User;
import com.jrobertgardzinski.security.domain.repository.UserRepository;
import com.jrobertgardzinski.util.constraint.Decision;
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

    record Given(Email email, PlaintextPassword password, HashedPassword hash) {};
    private static final Given GIVEN = new Given(
            Email.of("user@example.com"),
            PlaintextPassword.of("plaintext"),
            new HashedPassword("hash"));

    private UserRepository userRepository;
    private CanRegister canRegister;
    private CreatePasswordHash createPasswordHash;
    private Register register;

    @BeforeTry
    void init() {
        userRepository = Mockito.mock(UserRepository.class);
        canRegister = Mockito.mock(CanRegister.class);
        createPasswordHash = Mockito.mock(CreatePasswordHash.class);
        register = new Register(userRepository, canRegister, createPasswordHash);
    }

    @Property
    @Label("Invalid when email, password, or both fail validation")
    void invalid_when_any_validation_fails(
            @ForAll boolean emailFails,
            @ForAll boolean passwordFails) {

        Assume.that(emailFails || passwordFails);

        List<String> emailErrors = emailFails ? someErrors() : Collections.emptyList();
        List<String> passwordErrors = passwordFails ? someErrors() : Collections.emptyList();
        Decision<Email> decision = emailDecision(emailErrors);
        Outcome<HashedPassword> outcome = passwordOutcome(passwordErrors);

        Mockito.when(canRegister.evaluate(GIVEN.email)).thenReturn(decision);
        Mockito.when(createPasswordHash.create(GIVEN.password)).thenReturn(outcome);

        RegisterResult result = register.execute(GIVEN.email, GIVEN.password);

        RegisterResult.Invalid invalid = assertInstanceOf(RegisterResult.Invalid.class, result);
        assertAll(
                () -> assertEquals(emailErrors, invalid.emailErrors()),
                () -> assertEquals(passwordErrors, invalid.passwordErrors()),
                () -> Mockito.verify(userRepository, Mockito.never()).save(Mockito.any())
        );
    }
    @SuppressWarnings("unchecked")
    private List<String> someErrors() {
        List<String> errors = Mockito.mock(List.class);
        Mockito.when(errors.isEmpty()).thenReturn(false);
        return errors;
    }
    private Decision<Email> emailDecision(List<String> errors) {
        return errors.isEmpty() ? new Decision.Allowed<>() : new Decision.Rejected<>(errors);
    }
    private Outcome<HashedPassword> passwordOutcome(List<String> errors) {
        return errors.isEmpty() ? new Outcome.Allowed<>(GIVEN.hash) : new Outcome.Rejected<>(errors);
    }

    @Example
    @Label("Valid when both email and password pass validation")
    void valid_when_both_pass() {
        User user = new User(GIVEN.email, GIVEN.hash);
        Mockito.when(canRegister.evaluate(GIVEN.email)).thenReturn(new Decision.Allowed<>());
        Mockito.when(createPasswordHash.create(GIVEN.password)).thenReturn(new Outcome.Allowed<>(GIVEN.hash));
        Mockito.when(userRepository.save(Mockito.any())).thenReturn(user);

        RegisterResult result = register.execute(GIVEN.email, GIVEN.password);

        RegisterResult.Valid valid = assertInstanceOf(RegisterResult.Valid.class, result);
        assertEquals(user, valid.user());
    }
}

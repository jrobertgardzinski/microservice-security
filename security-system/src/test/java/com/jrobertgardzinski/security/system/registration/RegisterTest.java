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

    private static final String EMAIL = "user@example.com";
    private static final String PASSWORD = "plaintext";
    private static final HashedPassword HASH = new HashedPassword("hash");

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
    @Label("Rejected when email, password, or both fail validation")
    void rejected_when_any_validation_fails(
            @ForAll boolean emailFails,
            @ForAll boolean passwordFails) {

        Assume.that(emailFails || passwordFails);

        List<String> emailErrors = emailFails ? someErrors() : Collections.emptyList();
        List<String> passwordErrors = passwordFails ? someErrors() : Collections.emptyList();

        Decision<Email> decision = emailDecision(emailErrors);
        Outcome<HashedPassword> outcome = passwordOutcome(passwordErrors);
        Mockito.when(canRegister.evaluate(Mockito.any())).thenReturn(decision);
        Mockito.when(createPasswordHash.create(Mockito.any())).thenReturn(outcome);

        RegisterResult result = register.execute(() -> Email.of(EMAIL), () -> PlaintextPassword.of(PASSWORD));

        RegisterResult.Rejected rejected = assertInstanceOf(RegisterResult.Rejected.class, result);
        assertAll(
                () -> assertEquals(emailErrors, rejected.emailErrors()),
                () -> assertEquals(passwordErrors, rejected.passwordErrors()),
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
        return errors.isEmpty() ? new Outcome.Allowed<>(HASH) : new Outcome.Rejected<>(errors);
    }

    @Example
    @Label("Registered when both email and password pass validation")
    void registered_when_both_pass() {
        User user = new User(Email.of(EMAIL), HASH);
        Mockito.when(canRegister.evaluate(Mockito.any())).thenReturn(new Decision.Allowed<>());
        Mockito.when(createPasswordHash.create(Mockito.any())).thenReturn(new Outcome.Allowed<>(HASH));
        Mockito.when(userRepository.save(Mockito.any())).thenReturn(user);

        RegisterResult result = register.execute(() -> Email.of(EMAIL), () -> PlaintextPassword.of(PASSWORD));

        RegisterResult.Registered registered = assertInstanceOf(RegisterResult.Registered.class, result);
        assertEquals(user, registered.user());
    }
}

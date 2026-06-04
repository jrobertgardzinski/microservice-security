package com.jrobertgardzinski.security.system.registration;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.email.domain.NormalizedEmail;
import com.jrobertgardzinski.email.policy.CanRegister;
import com.jrobertgardzinski.password.domain.HashedPassword;
import com.jrobertgardzinski.password.domain.PlaintextPassword;
import com.jrobertgardzinski.password.policy.CreatePasswordHash;
import com.jrobertgardzinski.security.domain.entity.User;
import com.jrobertgardzinski.security.domain.repository.UserRepository;
import com.jrobertgardzinski.util.constraint.Decision;
import com.jrobertgardzinski.util.constraint.Outcome;
import io.qameta.allure.Epic;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeTry;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@Epic("Register")
class RegisterTest {

    private static final Email EMAIL = Email.of("user@example.com");
    private static final PlaintextPassword PASSWORD = PlaintextPassword.of("plaintext");
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

    @Provide
    Arbitrary<List<String>> errorCodes() {
        return Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20)
                .list().ofMaxSize(5);
    }
    @Property
    @Label("Invalid when email, password, or both fail validation")
    void invalid_when_any_validation_fails(
            @ForAll("errorCodes") List<String> emailErrors,
            @ForAll("errorCodes") List<String> passwordErrors) {
        Assume.that(!emailErrors.isEmpty() || !passwordErrors.isEmpty());

        Mockito.when(canRegister.evaluate(EMAIL)).thenReturn(emailDecision(emailErrors));
        Mockito.when(createPasswordHash.create(PASSWORD)).thenReturn(passwordOutcome(passwordErrors));

        RegisterResult result = register.execute(EMAIL, PASSWORD);

        RegisterResult.Invalid invalid = assertInstanceOf(RegisterResult.Invalid.class, result);
        assertAll(
                () -> assertEquals(emailErrors, invalid.emailErrors()),
                () -> assertEquals(passwordErrors, invalid.passwordErrors()),
                () -> Mockito.verify(userRepository, Mockito.never()).save(Mockito.any())
        );
    }
    private Decision<Email> emailDecision(List<String> errors) {
        return errors.isEmpty() ? new Decision.Allowed<>() : new Decision.Rejected<>(errors);
    }
    private Outcome<HashedPassword> passwordOutcome(List<String> errors) {
        return errors.isEmpty() ? new Outcome.Allowed<>(HASH) : new Outcome.Rejected<>(errors);
    }

    @Example
    @Label("Valid when both email and password pass validation")
    void valid_when_both_pass() {
        UUID uuid = Mockito.mock(UUID.class);
        try (MockedStatic<UUID> uuidMock = Mockito.mockStatic(UUID.class)) {
            uuidMock.when(UUID::randomUUID).thenReturn(uuid);

            User userToSave = new User(EMAIL, HASH);
            User savedUser = userToSave;
            Mockito.when(canRegister.evaluate(EMAIL)).thenReturn(new Decision.Allowed<>());
            Mockito.when(createPasswordHash.create(PASSWORD)).thenReturn(new Outcome.Allowed<>(HASH));
            Mockito.when(userRepository.save(userToSave)).thenReturn(savedUser);

            RegisterResult result = register.execute(EMAIL, PASSWORD);

            RegisterResult.Valid valid = assertInstanceOf(RegisterResult.Valid.class, result);
            assertEquals(savedUser, valid.user());
        }
    }
}

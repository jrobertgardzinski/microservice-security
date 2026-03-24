package com.jrobertgardzinski.security.system.factory;

import com.jrobertgardzinski.password.policy.*;
import com.jrobertgardzinski.security.domain.vo.UserRegistration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.jrobertgardzinski.security.system.TestData.VALID_PASSWORD;
import static org.junit.jupiter.api.Assertions.*;

class RegisterFactoryTest {

    private final RegisterFactory factory = new RegisterFactory(List.of(
            new _MinLengthConstraint(12),
            new _ContainsLowercaseConstraint(),
            new _ContainsUppercaseConstraint(),
            new _ContainsDigitConstraint(),
            new _ContainsSpecialCharConstraint("#?!")
    ));

    @Test
    void shouldThrowWhenEmailAndPasswordInvalid() {
        UserRegistrationValidationException exception = assertThrows(
            UserRegistrationValidationException.class,
            () -> factory.create("blah", "123")
        );

        assertTrue(exception.hasEmailErrors());
        assertTrue(exception.hasPasswordErrors());
        assertTrue(exception.emailErrors().stream().anyMatch(e -> e.contains("does not meet regex")));
        assertTrue(exception.passwordErrors().stream().anyMatch(e -> e.contains("MIN_LENGTH_NOT_MET")));
    }

    @Test
    void shouldReturnUserRegistrationWhenValid() throws UserRegistrationValidationException {
        UserRegistration registration = factory.create("test@example.com", VALID_PASSWORD);

        assertNotNull(registration);
        assertEquals("test@example.com", registration.email().value());
    }

    @Test
    void shouldCollectAllPasswordViolations() {
        UserRegistrationValidationException exception = assertThrows(
            UserRegistrationValidationException.class,
            () -> factory.create("test@example.com", "weak")
        );

        assertFalse(exception.hasEmailErrors());
        assertTrue(exception.hasPasswordErrors());
        assertTrue(exception.passwordErrors().size() >= 4);
    }
}

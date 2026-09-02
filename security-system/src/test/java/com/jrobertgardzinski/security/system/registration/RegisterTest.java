package com.jrobertgardzinski.security.system.registration;

import com.jrobertgardzinski.email.config.BlockedDomains;
import com.jrobertgardzinski.email.config.CanRegisterConfig;
import com.jrobertgardzinski.email.domain.DomainPart;
import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.password.domain.HashAlgorithmPort;
import com.jrobertgardzinski.password.domain.HashedPassword;
import com.jrobertgardzinski.password.domain.PlaintextPassword;
import com.jrobertgardzinski.password.policy.PasswordPolicy;
import com.jrobertgardzinski.security.domain.entity.User;
import com.jrobertgardzinski.security.domain.repository.UserRepository;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeTry;
import org.mockito.Mockito;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@Epic("Use case")
@Feature("Register")
class RegisterTest {

    private static final String EMAIL = "user@example.com";
    private static final String BLOCKED_EMAIL = "user@blocked.example";
    private static final String PASSWORD = "StrongPassword1!";
    private static final String WEAK_PASSWORD = "weak";
    private static final HashedPassword HASH = new HashedPassword("hash:" + PASSWORD);
    /** The email policy in force for every attempt here: one blocked domain, no other rule. */
    private static final CanRegisterConfig EMAIL_POLICY = new CanRegisterConfig(
            new BlockedDomains(Set.of(DomainPart.of("blocked.example"))), null, null);
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
    private Register register;

    @BeforeTry
    void init() {
        userRepository = Mockito.mock(UserRepository.class);
        register = new Register(userRepository, () -> EMAIL_POLICY, FAKE_ALGORITHM, PasswordPolicy::withDefaults);
    }

    @Property
    @Label("Rejected when email, password, or both fail validation — with both policies in force attached")
    void rejected_when_any_validation_fails(
            @ForAll boolean emailFails,
            @ForAll boolean passwordFails) {

        Assume.that(emailFails || passwordFails);

        String email = emailFails ? BLOCKED_EMAIL : EMAIL;
        String password = passwordFails ? WEAK_PASSWORD : PASSWORD;

        RegisterResult result = register.execute(() -> Email.of(email), () -> PlaintextPassword.of(password));

        RegisterResult.Rejected rejected = assertInstanceOf(RegisterResult.Rejected.class, result);
        assertAll(
                () -> assertEquals(emailFails ? List.of("DOMAIN_BLOCKED") : List.of(), rejected.emailErrors().codes()),
                () -> assertEquals(passwordFails, !rejected.passwordErrors().codes().isEmpty()),
                () -> assertEquals(EMAIL_POLICY, rejected.emailPolicy()),
                () -> assertEquals(PasswordPolicy.withDefaults(), rejected.passwordPolicy()),
                () -> Mockito.verify(userRepository, Mockito.never()).save(Mockito.any())
        );
    }

    @Example
    @Label("Registered when both email and password pass validation")
    void registered_when_both_pass() {
        User user = new User(Email.of(EMAIL), HASH);
        Mockito.when(userRepository.save(Mockito.any())).thenReturn(user);

        RegisterResult result = register.execute(() -> Email.of(EMAIL), () -> PlaintextPassword.of(PASSWORD));

        RegisterResult.Registered registered = assertInstanceOf(RegisterResult.Registered.class, result);
        assertEquals(user, registered.user());
    }

    @Example
    @Label("EmailAlreadyTaken when a user with that email already exists")
    void email_already_taken_when_user_exists() {
        Mockito.when(userRepository.existsBy(Mockito.any())).thenReturn(true);

        RegisterResult result = register.execute(() -> Email.of(EMAIL), () -> PlaintextPassword.of(PASSWORD));

        RegisterResult.EmailAlreadyTaken alreadyTaken = assertInstanceOf(RegisterResult.EmailAlreadyTaken.class, result);
        assertAll(
                () -> assertEquals(Email.of(EMAIL), alreadyTaken.email()),
                () -> Mockito.verify(userRepository, Mockito.never()).save(Mockito.any())
        );
    }
}

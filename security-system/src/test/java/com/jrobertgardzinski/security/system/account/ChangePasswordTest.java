package com.jrobertgardzinski.security.system.account;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.password.domain.HashAlgorithmPort;
import com.jrobertgardzinski.password.domain.HashedPassword;
import com.jrobertgardzinski.password.domain.PlaintextPassword;
import com.jrobertgardzinski.password.policy.CreatePasswordHash;
import com.jrobertgardzinski.password.policy.PasswordPolicy;
import com.jrobertgardzinski.security.domain.entity.User;
import com.jrobertgardzinski.security.domain.repository.UserRepository;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import net.jqwik.api.Example;
import net.jqwik.api.Label;
import net.jqwik.api.lifecycle.BeforeTry;
import org.mockito.Mockito;

import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@Epic("Use case")
@Feature("Change password")
class ChangePasswordTest {

    private static final Email EMAIL = Email.of("user@example.com");
    private static final Supplier<PlaintextPassword> CURRENT = () -> PlaintextPassword.of("OldPassword1!");
    private static final Supplier<PlaintextPassword> NEW_STRONG = () -> PlaintextPassword.of("NewPassword1!");
    private static final Supplier<PlaintextPassword> NEW_WEAK = () -> PlaintextPassword.of("weak");

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
    private ChangePassword changePassword;

    @BeforeTry
    void init() {
        userRepository = Mockito.mock(UserRepository.class);
        Mockito.when(userRepository.findBy(EMAIL)).thenReturn(Optional.of(
                new User(EMAIL, new HashedPassword("hash:OldPassword1!"))));
        changePassword = new ChangePassword(userRepository, FAKE_ALGORITHM,
                new CreatePasswordHash(FAKE_ALGORITHM, PasswordPolicy.withDefaults()));
    }

    @Example
    @Label("Correct current password and a strong new one change the password")
    void changes_the_password() {
        assertInstanceOf(ChangePasswordResult.Changed.class, changePassword.execute(EMAIL, CURRENT, NEW_STRONG));

        Mockito.verify(userRepository).updatePassword(EMAIL, new HashedPassword("hash:NewPassword1!"));
    }

    @Example
    @Label("A wrong current password is rejected and nothing changes")
    void wrong_current_password_is_rejected() {
        Supplier<PlaintextPassword> wrong = () -> PlaintextPassword.of("WrongPassword1!");

        assertInstanceOf(ChangePasswordResult.WrongCurrentPassword.class,
                changePassword.execute(EMAIL, wrong, NEW_STRONG));

        Mockito.verify(userRepository, Mockito.never()).updatePassword(Mockito.any(), Mockito.any());
    }

    @Example
    @Label("A weak new password is rejected and nothing changes")
    void weak_new_password_is_rejected() {
        assertInstanceOf(ChangePasswordResult.WeakPassword.class, changePassword.execute(EMAIL, CURRENT, NEW_WEAK));

        Mockito.verify(userRepository, Mockito.never()).updatePassword(Mockito.any(), Mockito.any());
    }
}

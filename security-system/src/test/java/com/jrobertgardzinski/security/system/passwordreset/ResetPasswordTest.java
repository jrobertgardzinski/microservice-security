package com.jrobertgardzinski.security.system.passwordreset;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.password.domain.HashAlgorithmPort;
import com.jrobertgardzinski.password.domain.HashedPassword;
import com.jrobertgardzinski.password.domain.PlaintextPassword;
import com.jrobertgardzinski.password.policy.CreatePasswordHash;
import com.jrobertgardzinski.password.policy.PasswordPolicy;
import com.jrobertgardzinski.security.domain.repository.PasswordResetRepository;
import com.jrobertgardzinski.security.domain.repository.UserRepository;
import com.jrobertgardzinski.security.domain.vo.token.PasswordResetToken;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import net.jqwik.api.Example;
import net.jqwik.api.Label;
import net.jqwik.api.lifecycle.BeforeTry;
import org.mockito.Mockito;

import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@Epic("Use case")
@Feature("Reset password")
class ResetPasswordTest {

    private static final PasswordResetToken TOKEN = new PasswordResetToken("reset-token");
    private static final Email EMAIL = Email.of("user@example.com");
    private static final Supplier<PlaintextPassword> STRONG = () -> PlaintextPassword.of("NewPassword1!");
    private static final Supplier<PlaintextPassword> WEAK = () -> PlaintextPassword.of("weak");

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

    private PasswordResetRepository passwordResetRepository;
    private UserRepository userRepository;
    private ResetPassword resetPassword;

    @BeforeTry
    void init() {
        passwordResetRepository = Mockito.mock(PasswordResetRepository.class);
        userRepository = Mockito.mock(UserRepository.class);
        resetPassword = new ResetPassword(passwordResetRepository, userRepository,
                new CreatePasswordHash(FAKE_ALGORITHM, PasswordPolicy.withDefaults()));
    }

    @Example
    @Label("A valid token and strong password reset the password")
    void valid_token_resets_the_password() {
        Mockito.when(passwordResetRepository.consumeReset(TOKEN)).thenReturn(Optional.of(EMAIL));

        ResetPasswordResult result = resetPassword.execute(TOKEN, STRONG);

        assertEquals(new ResetPasswordResult.PasswordReset(EMAIL), result);
        Mockito.verify(userRepository).updatePassword(EMAIL, new HashedPassword("hash:NewPassword1!"));
    }

    @Example
    @Label("An unknown token is rejected and no password is changed")
    void unknown_token_is_rejected() {
        Mockito.when(passwordResetRepository.consumeReset(TOKEN)).thenReturn(Optional.empty());

        assertInstanceOf(ResetPasswordResult.InvalidToken.class, resetPassword.execute(TOKEN, STRONG));

        Mockito.verify(userRepository, Mockito.never()).updatePassword(Mockito.any(), Mockito.any());
    }

    @Example
    @Label("A weak new password is rejected without consuming the token")
    void weak_password_is_rejected() {
        assertInstanceOf(ResetPasswordResult.WeakPassword.class, resetPassword.execute(TOKEN, WEAK));

        Mockito.verify(passwordResetRepository, Mockito.never()).consumeReset(Mockito.any());
        Mockito.verify(userRepository, Mockito.never()).updatePassword(Mockito.any(), Mockito.any());
    }
}

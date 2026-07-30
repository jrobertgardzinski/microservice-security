package com.jrobertgardzinski.security.system.passwordreset;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.password.domain.HashAlgorithmPort;
import com.jrobertgardzinski.password.domain.HashedPassword;
import com.jrobertgardzinski.password.domain.PlaintextPassword;
import com.jrobertgardzinski.password.policy.CreatePasswordHash;
import com.jrobertgardzinski.password.policy.PasswordPolicy;
import com.jrobertgardzinski.security.domain.repository.AuthorizationDataRepository;
import com.jrobertgardzinski.security.domain.repository.PasswordResetRepository;
import com.jrobertgardzinski.security.domain.repository.UserRepository;
import com.jrobertgardzinski.security.domain.vo.token.PasswordResetToken;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import net.jqwik.api.Example;
import net.jqwik.api.Label;
import net.jqwik.api.lifecycle.BeforeTry;
import org.mockito.Mockito;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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

    private static final Duration TTL = Duration.ofMinutes(60);
    /** A steerable clock: "now" is fixed so the age of a reset is a fact of the test, not of the wall. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-30T12:00:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);

    private PasswordResetRepository passwordResetRepository;
    private UserRepository userRepository;
    private AuthorizationDataRepository sessions;
    private ResetPassword resetPassword;

    @BeforeTry
    void init() {
        passwordResetRepository = Mockito.mock(PasswordResetRepository.class);
        userRepository = Mockito.mock(UserRepository.class);
        sessions = Mockito.mock(AuthorizationDataRepository.class);
        resetPassword = new ResetPassword(passwordResetRepository, userRepository,
                new CreatePasswordHash(FAKE_ALGORITHM, PasswordPolicy.withDefaults()),
                new com.jrobertgardzinski.security.domain.repository.PasswordlessAccountRepository() {
                    public boolean isPasswordless(com.jrobertgardzinski.email.domain.Email e) { return false; }
                    public void setPasswordless(com.jrobertgardzinski.email.domain.Email e, boolean v) {}
                    public void reassign(com.jrobertgardzinski.email.domain.Email f, com.jrobertgardzinski.email.domain.Email t) {}
                    public void purge(com.jrobertgardzinski.email.domain.Email e) {}
                }, sessions, TTL, CLOCK);
    }

    @Example
    @Label("A valid token and strong password reset the password")
    void valid_token_resets_the_password() {
        Mockito.when(passwordResetRepository.consumeReset(TOKEN)).thenReturn(
                Optional.of(new PasswordResetRepository.PendingReset(EMAIL, NOW.minusMinutes(5))));

        ResetPasswordResult result = resetPassword.execute(TOKEN, STRONG);

        assertEquals(new ResetPasswordResult.PasswordReset(EMAIL), result);
        Mockito.verify(userRepository).updatePassword(EMAIL, new HashedPassword("hash:NewPassword1!"));
    }

    @Example
    @Label("A completed reset revokes every session the old password left behind")
    void a_reset_revokes_every_existing_session() {
        // A reset is what a user reaches for when they believe someone else is on their account.
        // Sessions are rows unrelated to the password hash, so replacing the hash alone left a
        // stolen access token authorizing — and rotating itself on /refresh — for the whole refresh
        // window after the victim did exactly what every guide tells them to do.
        Mockito.when(passwordResetRepository.consumeReset(TOKEN)).thenReturn(
                Optional.of(new PasswordResetRepository.PendingReset(EMAIL, NOW.minusMinutes(5))));

        assertInstanceOf(ResetPasswordResult.PasswordReset.class, resetPassword.execute(TOKEN, STRONG));

        Mockito.verify(sessions).revokeAllSessions(EMAIL);
    }

    @Example
    @Label("A refused reset leaves the sessions alone")
    void a_refused_reset_revokes_nothing() {
        Mockito.when(passwordResetRepository.consumeReset(TOKEN)).thenReturn(Optional.empty());

        assertInstanceOf(ResetPasswordResult.InvalidToken.class, resetPassword.execute(TOKEN, STRONG));

        Mockito.verify(sessions, Mockito.never()).revokeAllSessions(Mockito.any());
    }

    @Example
    @Label("An unknown token is rejected and no password is changed")
    void unknown_token_is_rejected() {
        Mockito.when(passwordResetRepository.consumeReset(TOKEN)).thenReturn(Optional.empty());

        assertInstanceOf(ResetPasswordResult.InvalidToken.class, resetPassword.execute(TOKEN, STRONG));

        Mockito.verify(userRepository, Mockito.never()).updatePassword(Mockito.any(), Mockito.any());
    }

    @Example
    @Label("A token older than the TTL is refused, and refused as if it were unknown")
    void an_aged_token_is_refused() {
        // password_resets carried no timestamp at all, so a link only ever died by being used. Matched
        // by ADDRESS, such a link outlives the account: after the account closes and the address is
        // registered by someone else, redeeming it sets THAT person's password. The verdict is
        // InvalidToken, not a distinct "expired" — telling the two apart would confirm to a stranger
        // that the address has an account.
        Mockito.when(passwordResetRepository.consumeReset(TOKEN)).thenReturn(
                Optional.of(new PasswordResetRepository.PendingReset(EMAIL, NOW.minus(TTL).minusSeconds(1))));

        assertInstanceOf(ResetPasswordResult.InvalidToken.class, resetPassword.execute(TOKEN, STRONG));

        // consumed all the same: an expired link must not stay redeemable after a failed attempt
        Mockito.verify(passwordResetRepository).consumeReset(TOKEN);
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

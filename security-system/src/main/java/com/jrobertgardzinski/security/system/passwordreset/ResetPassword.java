package com.jrobertgardzinski.security.system.passwordreset;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.password.domain.HashAlgorithmPort;
import com.jrobertgardzinski.password.domain.HashedPassword;
import com.jrobertgardzinski.password.domain.PlaintextPassword;
import com.jrobertgardzinski.password.policy.CreatePasswordHash;
import com.jrobertgardzinski.password.policy.PasswordPolicy;
import com.jrobertgardzinski.security.domain.repository.AuthorizationDataRepository;
import com.jrobertgardzinski.security.domain.repository.PasswordResetRepository;
import com.jrobertgardzinski.security.domain.repository.PasswordlessAccountRepository;
import com.jrobertgardzinski.security.domain.repository.UserRepository;
import com.jrobertgardzinski.security.domain.vo.token.PasswordResetToken;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import com.jrobertgardzinski.password.policy.PasswordPolicyInForce;

import java.util.function.Supplier;

/**
 * Completes a password reset: the new password must meet the policy, the token must match a pending
 * reset, and that reset must still be young enough. The password is validated first so a weak
 * password does not burn the token; then the single-use token is consumed and the user's password
 * hash replaced. This is also how a federated (passwordless) account gains its first password — so
 * the account stops being passwordless and its password starts counting toward the MFA floor.
 *
 * <p>Single use alone was not enough: a reset is matched by ADDRESS, and an address outlives the
 * account that owned it. An unexpiring link e-mailed months ago would still be redeemable after the
 * account closed and the address was registered by someone else — and would then set THAT person's
 * password. An expired token is consumed and refused, and refused as {@code InvalidToken}: telling
 * "expired" from "unknown" apart would confirm to a stranger that the address has an account.
 *
 * <p><b>Every session goes with the old password.</b> Sessions are rows unrelated to the password
 * hash, so replacing the hash left them all authorizing — and a reset is what a user reaches for
 * PRECISELY when they believe someone else is on their account. A thief's stolen access token kept
 * working, and kept rotating itself on {@code /refresh}, for the whole refresh window after the
 * victim did the one thing every guide tells them to do. {@code revokeAllSessions} runs in the same
 * transaction as the new hash: either the password changed and no old session survived, or neither
 * happened.
 */
public class ResetPassword {

    private final PasswordResetRepository passwordResetRepository;
    private final UserRepository userRepository;
    private final HashAlgorithmPort hashAlgorithm;
    private final PasswordPolicyInForce passwordPolicy;
    private final PasswordlessAccountRepository passwordlessAccounts;
    private final AuthorizationDataRepository sessions;
    private final Duration tokenTtl;
    private final Clock clock;

    public ResetPassword(PasswordResetRepository passwordResetRepository, UserRepository userRepository,
                         HashAlgorithmPort hashAlgorithm, PasswordPolicyInForce passwordPolicy,
                         PasswordlessAccountRepository passwordlessAccounts,
                         AuthorizationDataRepository sessions, Duration tokenTtl, Clock clock) {
        this.passwordResetRepository = passwordResetRepository;
        this.userRepository = userRepository;
        this.hashAlgorithm = hashAlgorithm;
        this.passwordPolicy = passwordPolicy;
        this.passwordlessAccounts = passwordlessAccounts;
        this.sessions = sessions;
        this.tokenTtl = tokenTtl;
        this.clock = clock;
    }

    public ResetPasswordResult execute(PasswordResetToken token, Supplier<PlaintextPassword> newPassword) {
        Optional<HashedPassword> hashed = new CreatePasswordHash(hashAlgorithm, passwordPolicy.current())
                .create(newPassword).findValue();
        if (hashed.isEmpty()) {
            return new ResetPasswordResult.WeakPassword();
        }
        Optional<PasswordResetRepository.PendingReset> pending = passwordResetRepository.consumeReset(token);
        if (pending.isEmpty()) {
            return new ResetPasswordResult.InvalidToken();
        }
        PasswordResetRepository.PendingReset reset = pending.get();
        if (reset.requestedAt().plus(tokenTtl).isBefore(LocalDateTime.now(clock))) {
            return new ResetPasswordResult.InvalidToken();   // too old; consumed, so it is spent for good
        }
        Email email = reset.email();
        userRepository.updatePassword(email, hashed.get());
        passwordlessAccounts.setPasswordless(email, false);   // the account now has a password
        sessions.revokeAllSessions(email);   // the old password's sessions do not survive it
        return new ResetPasswordResult.PasswordReset(email);
    }
}

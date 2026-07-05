package com.jrobertgardzinski.security.system.passwordreset;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.password.domain.HashedPassword;
import com.jrobertgardzinski.password.domain.PlaintextPassword;
import com.jrobertgardzinski.password.policy.CreatePasswordHash;
import com.jrobertgardzinski.security.domain.repository.PasswordResetRepository;
import com.jrobertgardzinski.security.domain.repository.PasswordlessAccountRepository;
import com.jrobertgardzinski.security.domain.repository.UserRepository;
import com.jrobertgardzinski.security.domain.vo.token.PasswordResetToken;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Completes a password reset: the new password must meet the policy, and the token must match a
 * pending reset. The password is validated first so a weak password does not burn the token; then
 * the single-use token is consumed and the user's password hash replaced. This is also how a
 * federated (passwordless) account gains its first password — so the account stops being
 * passwordless and its password starts counting toward the MFA floor.
 */
public class ResetPassword {

    private final PasswordResetRepository passwordResetRepository;
    private final UserRepository userRepository;
    private final CreatePasswordHash createPasswordHash;
    private final PasswordlessAccountRepository passwordlessAccounts;

    public ResetPassword(PasswordResetRepository passwordResetRepository, UserRepository userRepository,
                         CreatePasswordHash createPasswordHash, PasswordlessAccountRepository passwordlessAccounts) {
        this.passwordResetRepository = passwordResetRepository;
        this.userRepository = userRepository;
        this.createPasswordHash = createPasswordHash;
        this.passwordlessAccounts = passwordlessAccounts;
    }

    public ResetPasswordResult execute(PasswordResetToken token, Supplier<PlaintextPassword> newPassword) {
        Optional<HashedPassword> hashed = createPasswordHash.create(newPassword).findValue();
        if (hashed.isEmpty()) {
            return new ResetPasswordResult.WeakPassword();
        }
        Optional<Email> email = passwordResetRepository.consumeReset(token);
        if (email.isEmpty()) {
            return new ResetPasswordResult.InvalidToken();
        }
        userRepository.updatePassword(email.get(), hashed.get());
        passwordlessAccounts.setPasswordless(email.get(), false);   // the account now has a password
        return new ResetPasswordResult.PasswordReset(email.get());
    }
}

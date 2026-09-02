package com.jrobertgardzinski.security.system.account;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.password.domain.HashAlgorithmPort;
import com.jrobertgardzinski.password.domain.HashedPassword;
import com.jrobertgardzinski.password.domain.PlaintextPassword;
import com.jrobertgardzinski.password.policy.CreatePasswordHash;
import com.jrobertgardzinski.password.policy.PasswordPolicy;
import com.jrobertgardzinski.security.domain.entity.User;
import com.jrobertgardzinski.security.domain.repository.AuthorizationDataRepository;
import com.jrobertgardzinski.security.domain.repository.UserRepository;

import java.util.Optional;
import com.jrobertgardzinski.password.policy.ladder.PasswordPolicyInForce;

import java.util.function.Supplier;

/**
 * Changes a signed-in user's password: the current password must match, and the new password must
 * meet the policy. The caller's identity comes from their access token, so only the email is needed.
 *
 * <p><b>Every session goes with the old password, including the caller's own.</b> Sessions are rows
 * unrelated to the password hash, so replacing the hash used to leave every one of them
 * authorizing — and changing a password is what a user does when they suspect somebody else is on
 * their account. The thief's token kept working, and kept rotating itself on {@code /refresh}, for
 * the whole refresh window. {@code revokeAllSessions} runs inside the caller's transaction, so the
 * new hash and the emptied session table commit together. Logging the caller out too is deliberate:
 * telling one live session from another needs the presented token here, and "sign in again with your
 * new password" is a smaller price than guessing which session is the owner's.
 */
public class ChangePassword {

    private final UserRepository userRepository;
    private final HashAlgorithmPort hashAlgorithm;
    private final PasswordPolicyInForce passwordPolicy;
    private final AuthorizationDataRepository sessions;

    public ChangePassword(UserRepository userRepository, HashAlgorithmPort hashAlgorithm,
                          PasswordPolicyInForce passwordPolicy, AuthorizationDataRepository sessions) {
        this.userRepository = userRepository;
        this.hashAlgorithm = hashAlgorithm;
        this.passwordPolicy = passwordPolicy;
        this.sessions = sessions;
    }

    public ChangePasswordResult execute(Email email, Supplier<PlaintextPassword> currentPassword,
                                        Supplier<PlaintextPassword> newPassword) {
        Optional<User> found = userRepository.findBy(email);
        if (found.isEmpty() || !hashAlgorithm.verify(found.get().passwordHash(), currentPassword.get())) {
            return new ChangePasswordResult.WrongCurrentPassword();
        }
        Optional<HashedPassword> newHash = new CreatePasswordHash(hashAlgorithm, passwordPolicy.current())
                .create(newPassword).findValue();
        if (newHash.isEmpty()) {
            return new ChangePasswordResult.WeakPassword();
        }
        userRepository.updatePassword(email, newHash.get());
        sessions.revokeAllSessions(email);   // the old password's sessions do not survive it
        return new ChangePasswordResult.Changed();
    }
}

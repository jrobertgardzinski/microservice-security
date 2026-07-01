package com.jrobertgardzinski.security.system.account;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.password.domain.HashAlgorithmPort;
import com.jrobertgardzinski.password.domain.HashedPassword;
import com.jrobertgardzinski.password.domain.PlaintextPassword;
import com.jrobertgardzinski.password.policy.CreatePasswordHash;
import com.jrobertgardzinski.security.domain.entity.User;
import com.jrobertgardzinski.security.domain.repository.UserRepository;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Changes a signed-in user's password: the current password must match, and the new password must
 * meet the policy. The caller's identity comes from their access token, so only the email is needed.
 */
public class ChangePassword {

    private final UserRepository userRepository;
    private final HashAlgorithmPort hashAlgorithm;
    private final CreatePasswordHash createPasswordHash;

    public ChangePassword(UserRepository userRepository, HashAlgorithmPort hashAlgorithm,
                          CreatePasswordHash createPasswordHash) {
        this.userRepository = userRepository;
        this.hashAlgorithm = hashAlgorithm;
        this.createPasswordHash = createPasswordHash;
    }

    public ChangePasswordResult execute(Email email, Supplier<PlaintextPassword> currentPassword,
                                        Supplier<PlaintextPassword> newPassword) {
        Optional<User> found = userRepository.findBy(email);
        if (found.isEmpty() || !hashAlgorithm.verify(found.get().passwordHash(), currentPassword.get())) {
            return new ChangePasswordResult.WrongCurrentPassword();
        }
        Optional<HashedPassword> newHash = createPasswordHash.create(newPassword).findValue();
        if (newHash.isEmpty()) {
            return new ChangePasswordResult.WeakPassword();
        }
        userRepository.updatePassword(email, newHash.get());
        return new ChangePasswordResult.Changed();
    }
}

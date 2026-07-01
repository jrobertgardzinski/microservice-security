package com.jrobertgardzinski.security.system.passwordreset;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.port.PasswordResetNotifier;
import com.jrobertgardzinski.security.domain.repository.PasswordResetRepository;
import com.jrobertgardzinski.security.domain.vo.token.PasswordResetToken;

/**
 * Starts a password reset: mints a single-use token, remembers it against the address, and e-mails
 * the reset link. Re-requesting simply issues a fresh token.
 */
public class RequestPasswordReset {

    private final PasswordResetRepository repository;
    private final PasswordResetNotifier notifier;

    public RequestPasswordReset(PasswordResetRepository repository, PasswordResetNotifier notifier) {
        this.repository = repository;
        this.notifier = notifier;
    }

    public void execute(Email email) {
        PasswordResetToken token = PasswordResetToken.random();
        repository.startReset(email, token);
        notifier.sendResetLink(email, token);
    }
}

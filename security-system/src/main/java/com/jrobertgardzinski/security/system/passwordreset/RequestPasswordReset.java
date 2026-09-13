package com.jrobertgardzinski.security.system.passwordreset;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.email.domain.NormalizedEmail;
import com.jrobertgardzinski.security.domain.port.PasswordResetNotifier;
import com.jrobertgardzinski.security.domain.repository.PasswordResetRepository;
import com.jrobertgardzinski.security.domain.repository.UserRepository;
import com.jrobertgardzinski.security.domain.vo.token.PasswordResetToken;

/**
 * Starts a password reset: mints a single-use token, remembers it against the address, and e-mails
 * the reset link. Re-requesting simply issues a fresh token.
 *
 * <p>An address with NO account is left alone, and quietly. It used to be minted and mailed for
 * like any other: a stranger typing addresses into the public endpoint had this service send a
 * "reset your password" mail to every one of them, which is both a mail nobody asked for and a
 * register of addresses somebody was curious about. The caller is told the same thing either way —
 * anti-enumeration lives at the HTTP boundary, and this decides only whether there is anything to
 * reset.
 */
public class RequestPasswordReset {

    private final PasswordResetRepository repository;
    private final UserRepository users;
    private final PasswordResetNotifier notifier;

    public RequestPasswordReset(PasswordResetRepository repository, UserRepository users,
                                PasswordResetNotifier notifier) {
        this.repository = repository;
        this.users = users;
        this.notifier = notifier;
    }

    public void execute(Email email) {
        if (!users.existsBy(NormalizedEmail.of(email))) {
            return;   // nothing to reset, and nobody to write to
        }
        PasswordResetToken token = PasswordResetToken.random();
        repository.startReset(email, token);
        notifier.sendResetLink(email, token);
    }
}

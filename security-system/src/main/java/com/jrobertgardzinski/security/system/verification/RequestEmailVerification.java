package com.jrobertgardzinski.security.system.verification;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.port.EmailVerificationNotifier;
import com.jrobertgardzinski.security.domain.repository.EmailVerificationRepository;
import com.jrobertgardzinski.security.domain.vo.token.VerificationToken;

/**
 * Starts e-mail verification: mints a single-use token, remembers it against the address, and
 * e-mails the verification link. Re-requesting simply issues a fresh token.
 */
public class RequestEmailVerification {

    private final EmailVerificationRepository repository;
    private final EmailVerificationNotifier notifier;

    public RequestEmailVerification(EmailVerificationRepository repository, EmailVerificationNotifier notifier) {
        this.repository = repository;
        this.notifier = notifier;
    }

    public void execute(Email email) {
        VerificationToken token = VerificationToken.random();
        repository.startVerification(email, token);
        notifier.sendVerificationLink(email, token);
    }
}

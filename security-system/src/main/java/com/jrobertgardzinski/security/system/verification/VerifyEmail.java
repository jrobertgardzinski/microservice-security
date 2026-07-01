package com.jrobertgardzinski.security.system.verification;

import com.jrobertgardzinski.security.domain.repository.EmailVerificationRepository;
import com.jrobertgardzinski.security.domain.vo.token.VerificationToken;

/**
 * Completes e-mail verification: a matching, unused token marks the address verified; an unknown or
 * already-used token is rejected.
 */
public class VerifyEmail {

    private final EmailVerificationRepository repository;

    public VerifyEmail(EmailVerificationRepository repository) {
        this.repository = repository;
    }

    public VerifyEmailResult execute(VerificationToken token) {
        return repository.completeVerification(token)
                .<VerifyEmailResult>map(VerifyEmailResult.Verified::new)
                .orElseGet(VerifyEmailResult.Rejected::new);
    }
}

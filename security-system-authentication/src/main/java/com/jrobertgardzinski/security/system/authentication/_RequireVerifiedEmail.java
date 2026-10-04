package com.jrobertgardzinski.security.system.authentication;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.repository.EmailVerificationRepository;

/**
 * Gate applied after the credentials check out: only a verified e-mail address may sign in. Keeps
 * an unverified (possibly hijacked-at-registration) address from becoming a working account.
 */
class _RequireVerifiedEmail {

    private final EmailVerificationRepository emailVerificationRepository;

    _RequireVerifiedEmail(EmailVerificationRepository emailVerificationRepository) {
        this.emailVerificationRepository = emailVerificationRepository;
    }

    boolean isVerified(Email email) {
        return emailVerificationRepository.isVerified(email);
    }
}

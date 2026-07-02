package com.jrobertgardzinski.security.system.account;

import com.jrobertgardzinski.security.domain.repository.EmailChangeRepository;
import com.jrobertgardzinski.security.domain.repository.EmailVerificationRepository;
import com.jrobertgardzinski.security.domain.repository.UserRepository;
import com.jrobertgardzinski.security.domain.vo.token.VerificationToken;

/**
 * Completes an email change: a matching, unused token applies the pending change (moving the user
 * to the new address); an unknown or already-used token is rejected. The new address is marked
 * verified — the change token was delivered there, which is the same ownership proof the regular
 * verification flow provides (and sign-in requires a verified address).
 */
public class ConfirmEmailChange {

    private final EmailChangeRepository emailChangeRepository;
    private final UserRepository userRepository;
    private final EmailVerificationRepository emailVerificationRepository;

    public ConfirmEmailChange(EmailChangeRepository emailChangeRepository, UserRepository userRepository,
                              EmailVerificationRepository emailVerificationRepository) {
        this.emailChangeRepository = emailChangeRepository;
        this.userRepository = userRepository;
        this.emailVerificationRepository = emailVerificationRepository;
    }

    public ConfirmEmailChangeResult execute(VerificationToken token) {
        return emailChangeRepository.confirmChange(token)
                .<ConfirmEmailChangeResult>map(change -> {
                    userRepository.updateEmail(change.currentEmail(), change.newEmail());
                    emailVerificationRepository.markVerified(change.newEmail());
                    return new ConfirmEmailChangeResult.EmailChanged(change.newEmail());
                })
                .orElseGet(ConfirmEmailChangeResult.InvalidToken::new);
    }
}

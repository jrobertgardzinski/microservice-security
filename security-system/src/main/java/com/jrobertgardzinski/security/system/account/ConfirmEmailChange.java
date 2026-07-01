package com.jrobertgardzinski.security.system.account;

import com.jrobertgardzinski.security.domain.repository.EmailChangeRepository;
import com.jrobertgardzinski.security.domain.repository.UserRepository;
import com.jrobertgardzinski.security.domain.vo.token.VerificationToken;

/**
 * Completes an email change: a matching, unused token applies the pending change (moving the user
 * to the new address); an unknown or already-used token is rejected.
 */
public class ConfirmEmailChange {

    private final EmailChangeRepository emailChangeRepository;
    private final UserRepository userRepository;

    public ConfirmEmailChange(EmailChangeRepository emailChangeRepository, UserRepository userRepository) {
        this.emailChangeRepository = emailChangeRepository;
        this.userRepository = userRepository;
    }

    public ConfirmEmailChangeResult execute(VerificationToken token) {
        return emailChangeRepository.confirmChange(token)
                .<ConfirmEmailChangeResult>map(change -> {
                    userRepository.updateEmail(change.currentEmail(), change.newEmail());
                    return new ConfirmEmailChangeResult.EmailChanged(change.newEmail());
                })
                .orElseGet(ConfirmEmailChangeResult.InvalidToken::new);
    }
}

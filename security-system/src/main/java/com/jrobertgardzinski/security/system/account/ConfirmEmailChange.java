package com.jrobertgardzinski.security.system.account;

import com.jrobertgardzinski.security.domain.repository.EmailChangeRepository;
import com.jrobertgardzinski.security.domain.repository.EmailVerificationRepository;
import com.jrobertgardzinski.security.domain.repository.FederatedIdentityRepository;
import com.jrobertgardzinski.security.domain.repository.UserRepository;
import com.jrobertgardzinski.security.domain.vo.token.VerificationToken;

/**
 * Completes an email change: a matching, unused token applies the pending change (moving the user
 * to the new address); an unknown or already-used token is rejected. The new address is marked
 * verified — the change token was delivered there, which is the same ownership proof the regular
 * verification flow provides (and sign-in requires a verified address). Federated links are
 * severed: the provider vouched for the OLD address, so they don't follow the account — the next
 * federated sign-in re-links through the ordinary verified-account auto-link.
 */
public class ConfirmEmailChange {

    private final EmailChangeRepository emailChangeRepository;
    private final UserRepository userRepository;
    private final EmailVerificationRepository emailVerificationRepository;
    private final FederatedIdentityRepository federatedIdentityRepository;

    public ConfirmEmailChange(EmailChangeRepository emailChangeRepository, UserRepository userRepository,
                              EmailVerificationRepository emailVerificationRepository,
                              FederatedIdentityRepository federatedIdentityRepository) {
        this.emailChangeRepository = emailChangeRepository;
        this.userRepository = userRepository;
        this.emailVerificationRepository = emailVerificationRepository;
        this.federatedIdentityRepository = federatedIdentityRepository;
    }

    public ConfirmEmailChangeResult execute(VerificationToken token) {
        return emailChangeRepository.confirmChange(token)
                .<ConfirmEmailChangeResult>map(change -> {
                    federatedIdentityRepository.unlinkAll(change.currentEmail());
                    userRepository.updateEmail(change.currentEmail(), change.newEmail());
                    emailVerificationRepository.markVerified(change.newEmail());
                    return new ConfirmEmailChangeResult.EmailChanged(change.newEmail());
                })
                .orElseGet(ConfirmEmailChangeResult.InvalidToken::new);
    }
}

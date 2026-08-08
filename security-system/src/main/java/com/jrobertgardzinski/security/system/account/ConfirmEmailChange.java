package com.jrobertgardzinski.security.system.account;

import com.jrobertgardzinski.security.domain.repository.EmailChangeRepository;
import com.jrobertgardzinski.security.domain.repository.EmailVerificationRepository;
import com.jrobertgardzinski.security.domain.repository.EnrolledFactorRepository;
import com.jrobertgardzinski.security.domain.repository.FederatedIdentityRepository;
import com.jrobertgardzinski.security.domain.repository.PasswordResetRepository;
import com.jrobertgardzinski.security.domain.repository.PasswordlessAccountRepository;
import com.jrobertgardzinski.security.domain.repository.RecoveryCodeRepository;
import com.jrobertgardzinski.security.domain.repository.UserRepository;
import com.jrobertgardzinski.security.domain.vo.token.VerificationToken;

/**
 * Completes an email change: a matching, unused token applies the pending change (moving the user
 * to the new address); an unknown or already-used token is rejected. The new address is marked
 * verified — the change token was delivered there, which is the same ownership proof the regular
 * verification flow provides (and sign-in requires a verified address). Federated links FOLLOW the
 * account: they are keyed by the provider's durable subject (the same person, the same Google
 * account), so they are re-pointed at the new address — severing them would orphan the identity,
 * because the provider keeps reporting its own old address and the auto-link at the next sign-in
 * would never find the moved account.
 *
 * <p>Everything else the account owns is keyed by that address too, and not one of those tables has
 * a foreign key to cascade — so this use case moves them by hand or the move loses them. Two kinds,
 * two answers. What belongs to the ACCOUNT follows it: the MFA factors, the recovery codes and the
 * passwordless mark. Left behind, a lookup under the new address finds nothing, so the second factor
 * disappears without a trace and a password alone signs in again; and a federated account reads as
 * "has a password" under its new address, which locks its owner out of ever deleting it. What was
 * E-MAILED TO THE OLD ADDRESS is dropped instead: a pending reset, a pending further change, the
 * verification row. The account no longer owns that mailbox, and a live token pointing at a freed
 * address sets the password of whoever registers it next.
 */
public class ConfirmEmailChange {

    private final EmailChangeRepository emailChangeRepository;
    private final UserRepository userRepository;
    private final EmailVerificationRepository emailVerificationRepository;
    private final FederatedIdentityRepository federatedIdentityRepository;
    private final EnrolledFactorRepository enrolledFactorRepository;
    private final RecoveryCodeRepository recoveryCodeRepository;
    private final PasswordlessAccountRepository passwordlessAccountRepository;
    private final PasswordResetRepository passwordResetRepository;

    public ConfirmEmailChange(EmailChangeRepository emailChangeRepository, UserRepository userRepository,
                              EmailVerificationRepository emailVerificationRepository,
                              FederatedIdentityRepository federatedIdentityRepository,
                              EnrolledFactorRepository enrolledFactorRepository,
                              RecoveryCodeRepository recoveryCodeRepository,
                              PasswordlessAccountRepository passwordlessAccountRepository,
                              PasswordResetRepository passwordResetRepository) {
        this.emailChangeRepository = emailChangeRepository;
        this.userRepository = userRepository;
        this.emailVerificationRepository = emailVerificationRepository;
        this.federatedIdentityRepository = federatedIdentityRepository;
        this.enrolledFactorRepository = enrolledFactorRepository;
        this.recoveryCodeRepository = recoveryCodeRepository;
        this.passwordlessAccountRepository = passwordlessAccountRepository;
        this.passwordResetRepository = passwordResetRepository;
    }

    public ConfirmEmailChangeResult execute(VerificationToken token) {
        return emailChangeRepository.confirmChange(token)
                .<ConfirmEmailChangeResult>map(change -> {
                    federatedIdentityRepository.relinkAll(change.currentEmail(), change.newEmail());
                    enrolledFactorRepository.reassign(change.currentEmail(), change.newEmail());
                    recoveryCodeRepository.reassign(change.currentEmail(), change.newEmail());
                    passwordlessAccountRepository.reassign(change.currentEmail(), change.newEmail());
                    passwordResetRepository.purge(change.currentEmail());
                    emailChangeRepository.purge(change.currentEmail());
                    emailVerificationRepository.purge(change.currentEmail());
                    userRepository.updateEmail(change.currentEmail(), change.newEmail());
                    emailVerificationRepository.markVerified(change.newEmail());
                    return new ConfirmEmailChangeResult.EmailChanged(change.newEmail());
                })
                .orElseGet(ConfirmEmailChangeResult.InvalidToken::new);
    }
}

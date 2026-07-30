package com.jrobertgardzinski.security.system.account;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.repository.AuthorizationDataRepository;
import com.jrobertgardzinski.security.domain.repository.EmailChangeRepository;
import com.jrobertgardzinski.security.domain.repository.EmailVerificationRepository;
import com.jrobertgardzinski.security.domain.repository.EnrolledFactorRepository;
import com.jrobertgardzinski.security.domain.repository.FederatedIdentityRepository;
import com.jrobertgardzinski.security.domain.repository.PasswordResetRepository;
import com.jrobertgardzinski.security.domain.repository.PasswordlessAccountRepository;
import com.jrobertgardzinski.security.domain.repository.RecoveryCodeRepository;
import com.jrobertgardzinski.security.domain.repository.UserRepository;

/**
 * Closes a user's account (GDPR right to be forgotten): revokes every session, drops the MFA
 * factors and recovery codes the account left behind (their secret hashes must not outlive the
 * account), severs the federated links (a stale link would let the old owner's Google identity
 * open whoever registers the freed address next), then deletes the user — so nothing of the
 * account can authenticate and no trace of its secrets remains. Idempotent.
 *
 * <p>Every one of those tables is keyed by the e-mail ADDRESS and not one of them has a foreign key,
 * so nothing cascades: whatever this use case does not name by hand outlives the account, and the
 * freed address can be registered by a stranger. Hence the pending-token tables are purged too — a
 * reset link e-mailed before the account closed would otherwise still be redeemable and, matched by
 * address alone, would set the password of the address's NEXT owner. So is the passwordless mark: it
 * would tell a step-up that the successor's account has no password to check.
 */
public class DeleteAccount {

    private final UserRepository userRepository;
    private final AuthorizationDataRepository authorizationDataRepository;
    private final EnrolledFactorRepository enrolledFactorRepository;
    private final RecoveryCodeRepository recoveryCodeRepository;
    private final FederatedIdentityRepository federatedIdentityRepository;
    private final EmailVerificationRepository emailVerificationRepository;
    private final PasswordResetRepository passwordResetRepository;
    private final EmailChangeRepository emailChangeRepository;
    private final PasswordlessAccountRepository passwordlessAccountRepository;

    public DeleteAccount(UserRepository userRepository, AuthorizationDataRepository authorizationDataRepository,
                         EnrolledFactorRepository enrolledFactorRepository,
                         RecoveryCodeRepository recoveryCodeRepository,
                         FederatedIdentityRepository federatedIdentityRepository,
                         EmailVerificationRepository emailVerificationRepository,
                         PasswordResetRepository passwordResetRepository,
                         EmailChangeRepository emailChangeRepository,
                         PasswordlessAccountRepository passwordlessAccountRepository) {
        this.userRepository = userRepository;
        this.authorizationDataRepository = authorizationDataRepository;
        this.enrolledFactorRepository = enrolledFactorRepository;
        this.recoveryCodeRepository = recoveryCodeRepository;
        this.federatedIdentityRepository = federatedIdentityRepository;
        this.emailVerificationRepository = emailVerificationRepository;
        this.passwordResetRepository = passwordResetRepository;
        this.emailChangeRepository = emailChangeRepository;
        this.passwordlessAccountRepository = passwordlessAccountRepository;
    }

    public void execute(Email email) {
        authorizationDataRepository.revokeAllSessions(email);
        enrolledFactorRepository.removeAll(email);
        recoveryCodeRepository.removeAll(email);
        federatedIdentityRepository.unlinkAll(email);
        passwordResetRepository.purge(email);
        emailChangeRepository.purge(email);
        emailVerificationRepository.purge(email);
        passwordlessAccountRepository.purge(email);
        userRepository.deleteByEmail(email);
    }
}

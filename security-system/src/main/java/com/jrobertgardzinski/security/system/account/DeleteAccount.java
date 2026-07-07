package com.jrobertgardzinski.security.system.account;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.repository.AuthorizationDataRepository;
import com.jrobertgardzinski.security.domain.repository.EnrolledFactorRepository;
import com.jrobertgardzinski.security.domain.repository.FederatedIdentityRepository;
import com.jrobertgardzinski.security.domain.repository.RecoveryCodeRepository;
import com.jrobertgardzinski.security.domain.repository.UserRepository;

/**
 * Closes a user's account (GDPR right to be forgotten): revokes every session, drops the MFA
 * factors and recovery codes the account left behind (their secret hashes must not outlive the
 * account), severs the federated links (a stale link would let the old owner's Google identity
 * open whoever registers the freed address next), then deletes the user — so nothing of the
 * account can authenticate and no trace of its secrets remains. Idempotent.
 */
public class DeleteAccount {

    private final UserRepository userRepository;
    private final AuthorizationDataRepository authorizationDataRepository;
    private final EnrolledFactorRepository enrolledFactorRepository;
    private final RecoveryCodeRepository recoveryCodeRepository;
    private final FederatedIdentityRepository federatedIdentityRepository;

    public DeleteAccount(UserRepository userRepository, AuthorizationDataRepository authorizationDataRepository,
                         EnrolledFactorRepository enrolledFactorRepository,
                         RecoveryCodeRepository recoveryCodeRepository,
                         FederatedIdentityRepository federatedIdentityRepository) {
        this.userRepository = userRepository;
        this.authorizationDataRepository = authorizationDataRepository;
        this.enrolledFactorRepository = enrolledFactorRepository;
        this.recoveryCodeRepository = recoveryCodeRepository;
        this.federatedIdentityRepository = federatedIdentityRepository;
    }

    public void execute(Email email) {
        authorizationDataRepository.revokeAllSessions(email);
        enrolledFactorRepository.removeAll(email);
        recoveryCodeRepository.removeAll(email);
        federatedIdentityRepository.unlinkAll(email);
        userRepository.deleteByEmail(email);
    }
}

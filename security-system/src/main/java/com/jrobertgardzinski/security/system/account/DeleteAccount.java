package com.jrobertgardzinski.security.system.account;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.core.FederatedIdentityRepository;
import com.jrobertgardzinski.security.domain.core.PasswordlessAccountRepository;
import com.jrobertgardzinski.security.domain.core.PersonalData;
import com.jrobertgardzinski.security.domain.core.Sessions;
import com.jrobertgardzinski.security.domain.core.UserRepository;

import java.util.List;


/**
 * Closes a user's account (GDPR right to be forgotten): revokes every session, drops the MFA
 * factors and recovery codes the account left behind (their secret hashes must not outlive the
 * account), severs the federated links (a stale link would let the old owner's Google identity
 * open whoever registers the freed address next), then deletes the user — so nothing of the
 * account can authenticate and no trace of its secrets remains. Idempotent.
 *
 * <p>Every one of those tables is keyed by the e-mail ADDRESS and not one of them has a foreign key,
 * so nothing cascades: whatever is not erased by hand outlives the account, and the freed address
 * can be registered by a stranger. The other areas erase their own through {@link PersonalData};
 * this use case keeps to core's tables. Hence the pending-token tables are purged too — a
 * reset link e-mailed before the account closed would otherwise still be redeemable and, matched by
 * address alone, would set the password of the address's NEXT owner. So is the passwordless mark: it
 * would tell a step-up that the successor's account has no password to check.
 */
public class DeleteAccount {

    private final UserRepository userRepository;
    private final Sessions sessions;
    private final FederatedIdentityRepository federatedIdentityRepository;
    private final PasswordlessAccountRepository passwordlessAccountRepository;
    private final List<PersonalData> personalData;

    /**
     * @param personalData what every area keeps about a person; this use case names none of them,
     *                     so an area that starts keeping something adds itself here, not a line below
     */
    public DeleteAccount(UserRepository userRepository, Sessions sessions,
                         FederatedIdentityRepository federatedIdentityRepository,
                         PasswordlessAccountRepository passwordlessAccountRepository,
                         List<PersonalData> personalData) {
        this.userRepository = userRepository;
        this.sessions = sessions;
        this.federatedIdentityRepository = federatedIdentityRepository;
        this.passwordlessAccountRepository = passwordlessAccountRepository;
        this.personalData = List.copyOf(personalData);
    }

    public void execute(Email email) {
        sessions.endAll(email);
        personalData.forEach(area -> area.erase(email));
        federatedIdentityRepository.unlinkAll(email);
        passwordlessAccountRepository.purge(email);
        userRepository.deleteByEmail(email);
    }
}

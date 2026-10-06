package com.jrobertgardzinski.security.system.mfa;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.core.PersonalData;
import com.jrobertgardzinski.security.domain.mfa.EnrolledFactorRepository;
import com.jrobertgardzinski.security.domain.mfa.RecoveryCodeRepository;

/**
 * What the mfa area keeps about a person: their enrolled factors and their recovery codes. Their
 * secret hashes must not outlive the account; on a new address they follow the person, or the
 * account would sign in without the factors it enrolled.
 */
public final class FactorData implements PersonalData {

    private final EnrolledFactorRepository enrolledFactors;
    private final RecoveryCodeRepository recoveryCodes;

    public FactorData(EnrolledFactorRepository enrolledFactors, RecoveryCodeRepository recoveryCodes) {
        this.enrolledFactors = enrolledFactors;
        this.recoveryCodes = recoveryCodes;
    }

    @Override
    public void erase(Email email) {
        enrolledFactors.removeAll(email);
        recoveryCodes.removeAll(email);
    }

    @Override
    public void move(Email from, Email to) {
        enrolledFactors.reassign(from, to);
        recoveryCodes.reassign(from, to);
    }
}

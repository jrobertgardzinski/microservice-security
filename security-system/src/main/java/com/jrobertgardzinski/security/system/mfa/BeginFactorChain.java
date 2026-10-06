package com.jrobertgardzinski.security.system.mfa;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.core.FactorChallenge;
import com.jrobertgardzinski.security.domain.core.SecondFactors;
import com.jrobertgardzinski.security.domain.core.Source;
import com.jrobertgardzinski.security.domain.mfa.EnrolledFactor;
import com.jrobertgardzinski.security.domain.mfa.EnrolledFactorRepository;
import com.jrobertgardzinski.security.domain.mfa.PendingAuthentication;
import com.jrobertgardzinski.security.domain.mfa.PendingAuthenticationStore;

import java.util.List;
import java.util.Optional;

/**
 * The mfa area's answer to {@link SecondFactors}: an account with factors gets its chain begun,
 * kept under a one-shot ticket, and the first factor's challenge out.
 */
public final class BeginFactorChain implements SecondFactors {

    private final EnrolledFactorRepository enrolledFactors;
    private final MfaChain chain;
    private final PendingAuthenticationStore pendingStore;

    public BeginFactorChain(EnrolledFactorRepository enrolledFactors, MfaChain chain,
                            PendingAuthenticationStore pendingStore) {
        this.enrolledFactors = enrolledFactors;
        this.chain = chain;
        this.pendingStore = pendingStore;
    }

    @Override
    public Optional<FactorChallenge> challenge(Email email, Optional<Source> source) {
        List<EnrolledFactor> factors = enrolledFactors.findByUser(email);
        if (factors.isEmpty()) {
            return Optional.empty();
        }
        PendingAuthentication begun = chain.begin(email, factors);
        PendingAuthentication pending = source.map(begun::startedFrom).orElse(begun);
        String ticket = pendingStore.open(pending);
        return Optional.of(new FactorChallenge(ticket, factors.get(0).type(), pending.challengeData()));
    }
}

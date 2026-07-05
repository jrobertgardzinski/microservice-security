package com.jrobertgardzinski.security.system.mfa;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.entity.EnrolledFactor;
import com.jrobertgardzinski.security.domain.repository.EnrolledFactorRepository;
import com.jrobertgardzinski.security.domain.vo.FactorType;

import java.util.Optional;

/**
 * Enrol a new factor for a signed-in user, in two steps that prove they control what they are
 * adding: {@link #start} issues the factor's challenge (a code to the address/number they gave),
 * {@link #confirm} accepts one correct proof and persists the factor. A factor cannot be added by
 * merely claiming an address — the same challenge-response that guards sign-in guards enrolment.
 */
public class EnrolFactor {

    public sealed interface Result {
        record ChallengeSent() implements Result {}
        record Enrolled(FactorType type) implements Result {}
        record WrongProof() implements Result {}
        record NoPendingEnrolment() implements Result {}
        record UnsupportedFactor() implements Result {}
    }

    private final FactorRegistry registry;
    private final EnrolledFactorRepository factors;
    private final EnrolmentChallengeStore pending;

    public EnrolFactor(FactorRegistry registry, EnrolledFactorRepository factors, EnrolmentChallengeStore pending) {
        this.registry = registry;
        this.factors = factors;
        this.pending = pending;
    }

    public Result start(Email user, FactorType type, String target) {
        Optional<AuthenticationFactor> factor = registry.forType(type);
        if (factor.isEmpty()) {
            return new Result.UnsupportedFactor();
        }
        EnrolledFactor candidate = candidate(user, type, target);
        Challenge challenge = factor.get().issueChallenge(candidate).orElse(null);
        pending.put(user, type, new EnrolmentChallengeStore.PendingEnrolment(target, challenge));
        return new Result.ChallengeSent();
    }

    public Result confirm(Email user, FactorType type, String proof) {
        Optional<AuthenticationFactor> factor = registry.forType(type);
        Optional<EnrolmentChallengeStore.PendingEnrolment> enrolment = pending.get(user, type);
        if (factor.isEmpty() || enrolment.isEmpty()) {
            return new Result.NoPendingEnrolment();
        }
        EnrolledFactor candidate = candidate(user, type, enrolment.get().target());
        if (!factor.get().verify(candidate, Optional.ofNullable(enrolment.get().challenge()), proof)) {
            return new Result.WrongProof();
        }
        factors.enrol(candidate);
        pending.remove(user, type);
        return new Result.Enrolled(type);
    }

    private EnrolledFactor candidate(Email user, FactorType type, String target) {
        // new factors go to the end of the chain, in enrolment order
        return new EnrolledFactor(user, type, FactorLabels.of(type), factors.findByUser(user).size(), target);
    }
}

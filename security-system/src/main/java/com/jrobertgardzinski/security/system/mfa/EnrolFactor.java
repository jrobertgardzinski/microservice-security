package com.jrobertgardzinski.security.system.mfa;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.entity.EnrolledFactor;
import com.jrobertgardzinski.security.domain.repository.EnrolledFactorRepository;
import com.jrobertgardzinski.security.domain.vo.FactorType;

import java.util.Optional;

/**
 * Enrol a new factor for a signed-in user, in two steps that prove they control what they are
 * adding: {@link #start} begins the factor (a code factor sends a code to the target; a possession
 * factor generates a secret and returns what to show — a TOTP {@code otpauth://} URI), {@link
 * #confirm} accepts one correct proof and persists the factor. A factor cannot be added by merely
 * claiming a target — the same verification that guards sign-in guards enrolment.
 */
public class EnrolFactor {

    public sealed interface Result {
        /** Enrolment begun; {@code display} is shown to the user (a TOTP otpauth URI) or empty (a code was sent). */
        record Started(String display) implements Result {}
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
        EnrolmentSetup setup = factor.get().beginEnrolment(target);
        pending.put(user, type, new EnrolmentChallengeStore.PendingEnrolment(setup.secretMaterial(), setup.challenge()));
        return new Result.Started(setup.display());
    }

    public Result confirm(Email user, FactorType type, String proof) {
        Optional<AuthenticationFactor> factor = registry.forType(type);
        Optional<EnrolmentChallengeStore.PendingEnrolment> enrolment = pending.get(user, type);
        if (factor.isEmpty() || enrolment.isEmpty()) {
            return new Result.NoPendingEnrolment();
        }
        EnrolledFactor candidate = candidate(user, type, enrolment.get().secretMaterial());
        if (!factor.get().verify(candidate, Optional.ofNullable(enrolment.get().challenge()), proof)) {
            return new Result.WrongProof();
        }
        // most factors store what enrolment produced; a factor whose real secret only arrives with
        // the confirming proof (WebAuthn's public key) distils it here, after verify has passed
        String stored = factor.get().enrolledMaterial(enrolment.get().secretMaterial(), proof);
        factors.enrol(candidate(user, type, stored));
        pending.remove(user, type);
        return new Result.Enrolled(type);
    }

    private EnrolledFactor candidate(Email user, FactorType type, String secretMaterial) {
        // new factors go to the end of the chain, in enrolment order
        return new EnrolledFactor(user, type, FactorLabels.of(type), factors.findByUser(user).size(), secretMaterial);
    }
}

package com.jrobertgardzinski.security.system.mfa;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.vo.FactorType;

import java.util.Optional;

/**
 * The half-finished enrolments — a user has asked to add a factor and a challenge went out, but the
 * proof has not come back yet. Keyed by (user, factor type), short-lived, in memory: losing one
 * only means the user restarts the enrolment.
 */
public interface EnrolmentChallengeStore {

    /** What a half-finished enrolment must remember: the factor's secret material and (for a
     *  challenge factor) the issued challenge to check the proof against. */
    record PendingEnrolment(String secretMaterial, Challenge challenge) {}

    void put(Email user, FactorType type, PendingEnrolment enrolment);

    Optional<PendingEnrolment> get(Email user, FactorType type);

    void remove(Email user, FactorType type);
}

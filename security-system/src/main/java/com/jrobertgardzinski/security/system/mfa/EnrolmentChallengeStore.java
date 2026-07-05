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

    record PendingEnrolment(String target, Challenge challenge) {}

    void put(Email user, FactorType type, PendingEnrolment enrolment);

    Optional<PendingEnrolment> get(Email user, FactorType type);

    void remove(Email user, FactorType type);
}

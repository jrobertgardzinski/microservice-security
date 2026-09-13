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

    /**
     * What a half-finished enrolment must remember: the factor's secret material, (for a challenge
     * factor) the issued challenge to check the proof against, and how many wrong proofs it will
     * still take.
     *
     * <p>The count is the same idea as the sign-in chain's: without it, confirming had no cap at
     * all, so a six-digit code could be walked through at the speed of the network for as long as
     * the entry lived. Enrolment is guarded by an elevated session and the entry's own TTL, which
     * is why this was small — not why it was right.
     */
    record PendingEnrolment(String secretMaterial, Challenge challenge, int attemptsLeft) {

        /** How many wrong proofs one enrolment attempt is worth — as at sign-in. */
        public static final int ATTEMPTS = 5;

        public static PendingEnrolment beginning(String secretMaterial, Challenge challenge) {
            return new PendingEnrolment(secretMaterial, challenge, ATTEMPTS);
        }

        public PendingEnrolment afterWrongProof() {
            return new PendingEnrolment(secretMaterial, challenge, attemptsLeft - 1);
        }
    }

    void put(Email user, FactorType type, PendingEnrolment enrolment);

    Optional<PendingEnrolment> get(Email user, FactorType type);

    void remove(Email user, FactorType type);
}

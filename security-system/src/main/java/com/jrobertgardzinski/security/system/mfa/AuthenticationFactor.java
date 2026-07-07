package com.jrobertgardzinski.security.system.mfa;

import com.jrobertgardzinski.security.domain.entity.EnrolledFactor;
import com.jrobertgardzinski.security.domain.vo.FactorType;

import java.util.Optional;

/**
 * One authentication method, as a plug. The whole point of MFA here: a new factor (TOTP, WebAuthn,
 * a hardware token…) is a new implementation of this interface registered as a bean — the chain
 * executor and everything above it never change. E-mail and SMS codes are just two implementations.
 *
 * <p>Two kinds exist. <b>Challenge-response</b> factors (e-mail, SMS) send something on
 * {@link #issueChallenge} and check it on {@link #verify}. <b>Possession</b> factors (TOTP,
 * WebAuthn) send nothing — {@code issueChallenge} is empty and {@code verify} checks the proof
 * against the enrolment directly (a time-window code, a signed nonce).
 */
public interface AuthenticationFactor {

    FactorType type();

    boolean needsChallenge();

    /**
     * Begin ENROLLING this factor for a user: challenge factors send a code to the requested target
     * and store it as their secret; possession factors generate a secret and hand back what the
     * user must see (e.g. a TOTP {@code otpauth://} URI). See {@link EnrolmentSetup}.
     */
    EnrolmentSetup beginEnrolment(String requestedTarget);

    /**
     * Begin this factor during SIGN-IN: send the code / mint the nonce, returning what must be
     * remembered until the proof arrives. Empty for possession factors, which need nothing sent.
     */
    Optional<Challenge> issueChallenge(EnrolledFactor enrolment);

    /** Whether {@code proof} satisfies this factor for the enrolment (and the challenge, if any). */
    boolean verify(EnrolledFactor enrolment, Optional<Challenge> challenge, String proof);

    /**
     * What to persist as the factor's secret once enrolment is CONFIRMED. Most factors store what
     * {@link #beginEnrolment} produced ({@code pendingMaterial}) unchanged — the default. A factor
     * whose real secret only arrives with the confirming proof (WebAuthn: the browser generates the
     * credential, so the public key is in the attestation) overrides this to distil it from
     * {@code proof} after {@link #verify} has passed.
     */
    default String enrolledMaterial(String pendingMaterial, String proof) {
        return pendingMaterial;
    }
}

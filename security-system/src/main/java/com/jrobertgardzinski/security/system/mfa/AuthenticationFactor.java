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
     * Begin this factor: send the code / mint the nonce, returning what must be remembered until
     * the proof arrives. Empty for possession factors, which need nothing remembered.
     */
    Optional<Challenge> issueChallenge(EnrolledFactor enrolment);

    /** Whether {@code proof} satisfies this factor for the enrolment (and the challenge, if any). */
    boolean verify(EnrolledFactor enrolment, Optional<Challenge> challenge, String proof);
}

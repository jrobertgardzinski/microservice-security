package com.jrobertgardzinski.security.domain.core;

import com.jrobertgardzinski.email.domain.Email;

import java.util.Optional;

/**
 * The second step of a sign-in. Asked once the first step has passed: an account with factors gets
 * a chain begun and its first challenge out; one without gets nothing, and signs in.
 */
public interface SecondFactors {

    /**
     * @param source where the sign-in began, so a wrong proof is charged as a wrong password is;
     *               empty for a sign-in through a provider, which guessed at nothing
     */
    Optional<FactorChallenge> challenge(Email email, Optional<Source> source);
}

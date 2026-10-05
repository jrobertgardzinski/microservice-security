package com.jrobertgardzinski.security.system.authentication;

import com.jrobertgardzinski.security.domain.entity.SessionTokens;
import com.jrobertgardzinski.security.domain.vo.FactorType;

/**
 * The outcome of presenting a factor proof against a sign-in in flight: the chain completed (here
 * is the session), another factor is now due, the proof was wrong (this many tries left), too many
 * wrong proofs tore the ticket down, or the ticket is unknown/expired.
 */
public sealed interface ContinueAuthenticationResult {

    record Completed(SessionTokens session) implements ContinueAuthenticationResult {}

    record NextFactor(FactorType type, String challengeData) implements ContinueAuthenticationResult {}

    record WrongProof(int attemptsLeft) implements ContinueAuthenticationResult {}

    record TooManyAttempts() implements ContinueAuthenticationResult {}

    record InvalidTicket() implements ContinueAuthenticationResult {}
}

package com.jrobertgardzinski.security.system.mfa;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.entity.EnrolledFactor;
import com.jrobertgardzinski.security.domain.vo.AttemptedAccount;
import com.jrobertgardzinski.security.domain.vo.FactorType;
import com.jrobertgardzinski.security.domain.vo.LockoutSubject;
import com.jrobertgardzinski.security.domain.vo.Source;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * A sign-in in flight: link #1 (password or an OAuth login) has passed, and these factors remain,
 * in order, before a session is minted. The current (first remaining) factor's challenge and the
 * attempts left on it are carried here; the whole thing lives briefly in a store keyed by a
 * one-shot ticket, exactly like the OAuth flow state.
 *
 * <p>{@code source} is where the sign-in BEGAN, and it is here so that a wrong proof can be charged
 * to the same pair a wrong password is — this address against this account. Without it the chain's
 * only counter was five attempts per ticket, which is a limit on one ticket and not on the person
 * minting tickets; {@code MfaChain}'s javadoc said a wrong proof "counts like a wrong password" and
 * nothing made it true.
 *
 * <p>It is an {@link Optional} because a STEP-UP chain runs through the same record and has no
 * sign-in attempt behind it: re-proving yourself inside a live session is not a guess at an
 * account, and it carries its own per-caller limit. Empty means "not a sign-in attempt", which is
 * a fact about the chain rather than a missing value.
 */
public record PendingAuthentication(Email email, Optional<Source> source, List<EnrolledFactor> remaining,
                                    Challenge currentChallenge, int attemptsLeft, LocalDateTime expiresAt) {

    public EnrolledFactor currentFactor() {
        return remaining.get(0);
    }

    public FactorType currentType() {
        return currentFactor().type();
    }

    public Optional<Challenge> challenge() {
        return Optional.ofNullable(currentChallenge);
    }

    /** The current factor's public challenge data to hand the client (a WebAuthn nonce), or null. */
    public String challengeData() {
        return currentChallenge == null ? null : currentChallenge.publicData();
    }

    public boolean isExpired(Clock clock) {
        return expiresAt.isBefore(LocalDateTime.now(clock));
    }

    /** The factors after the current one — what is still owed once this factor passes. */
    public List<EnrolledFactor> tail() {
        return remaining.subList(1, remaining.size());
    }

    public PendingAuthentication afterWrongProof() {
        return new PendingAuthentication(email, source, remaining, currentChallenge, attemptsLeft - 1, expiresAt);
    }

    /** The same chain, now known to have begun at this address — see the record's javadoc. */
    public PendingAuthentication startedFrom(Source source) {
        return new PendingAuthentication(email, Optional.of(source), remaining, currentChallenge,
                attemptsLeft, expiresAt);
    }

    /**
     * Who a wrong proof is charged to: this address against this account, the same pair a wrong
     * password is charged to. Empty for a step-up, which is not an attempt on an account.
     */
    public Optional<LockoutSubject> lockoutSubject() {
        return source.map(from -> new LockoutSubject(from, AttemptedAccount.of(email)));
    }
}

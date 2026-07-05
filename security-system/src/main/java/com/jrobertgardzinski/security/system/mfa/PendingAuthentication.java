package com.jrobertgardzinski.security.system.mfa;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.entity.EnrolledFactor;
import com.jrobertgardzinski.security.domain.vo.FactorType;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * A sign-in in flight: link #1 (password or an OAuth login) has passed, and these factors remain,
 * in order, before a session is minted. The current (first remaining) factor's challenge and the
 * attempts left on it are carried here; the whole thing lives briefly in a store keyed by a
 * one-shot ticket, exactly like the OAuth flow state.
 */
public record PendingAuthentication(Email email, List<EnrolledFactor> remaining, Challenge currentChallenge,
                                    int attemptsLeft, LocalDateTime expiresAt) {

    public EnrolledFactor currentFactor() {
        return remaining.get(0);
    }

    public FactorType currentType() {
        return currentFactor().type();
    }

    public Optional<Challenge> challenge() {
        return Optional.ofNullable(currentChallenge);
    }

    public boolean isExpired(Clock clock) {
        return expiresAt.isBefore(LocalDateTime.now(clock));
    }

    /** The factors after the current one — what is still owed once this factor passes. */
    public List<EnrolledFactor> tail() {
        return remaining.subList(1, remaining.size());
    }

    public PendingAuthentication afterWrongProof() {
        return new PendingAuthentication(email, remaining, currentChallenge, attemptsLeft - 1, expiresAt);
    }
}

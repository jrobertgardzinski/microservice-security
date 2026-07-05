package com.jrobertgardzinski.security.system.authentication;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.config.mfa.ChallengeCodeConfig;
import com.jrobertgardzinski.security.domain.entity.EnrolledFactor;
import com.jrobertgardzinski.security.system.mfa.Challenge;
import com.jrobertgardzinski.security.system.mfa.FactorRegistry;
import com.jrobertgardzinski.security.system.mfa.PendingAuthentication;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * The chain half of authentication: opens a pending authentication over the factors that remain
 * after link #1, issues each factor's challenge as it becomes current, and verifies a proof
 * against the current factor. It reaches into the {@link FactorRegistry} — which adapter serves a
 * type — so neither {@link Authentication} nor {@link ContinueAuthentication} needs to know.
 */
class _MfaChain {

    private final FactorRegistry registry;
    private final ChallengeCodeConfig config;
    private final Clock clock;
    private final int ticketTtlMinutes;

    _MfaChain(FactorRegistry registry, ChallengeCodeConfig config, Clock clock, int ticketTtlMinutes) {
        this.registry = registry;
        this.config = config;
        this.clock = clock;
        this.ticketTtlMinutes = ticketTtlMinutes;
    }

    /** Begin the tail: issue the first factor's challenge and build the pending authentication. */
    PendingAuthentication begin(Email email, List<EnrolledFactor> factors) {
        return new PendingAuthentication(email, List.copyOf(factors), issue(factors.get(0)),
                config.maxAttempts(), LocalDateTime.now(clock).plusMinutes(ticketTtlMinutes));
    }

    /** Advance to the next factor, keeping the ticket's overall expiry, resetting the attempt count. */
    PendingAuthentication advanceTo(PendingAuthentication pending, List<EnrolledFactor> tail) {
        return new PendingAuthentication(pending.email(), tail, issue(tail.get(0)),
                config.maxAttempts(), pending.expiresAt());
    }

    Challenge issue(EnrolledFactor factor) {
        return registry.forType(factor.type())
                .flatMap(f -> f.issueChallenge(factor))
                .orElse(null);   // possession factors carry no challenge
    }

    boolean verify(PendingAuthentication pending, String proof) {
        EnrolledFactor current = pending.currentFactor();
        return registry.forType(current.type())
                .map(f -> f.verify(current, pending.challenge(), proof))
                .orElse(false);
    }
}

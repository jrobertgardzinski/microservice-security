package com.jrobertgardzinski.security.domain.entity;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.vo.AuthSessionId;
import com.jrobertgardzinski.security.domain.vo.EmailOtpChallenge;
import com.jrobertgardzinski.security.domain.vo.FactorType;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * In-progress multi-factor authentication state.
 *
 * Holds which factors have already passed, which remain, and any per-factor
 * pending challenge (currently only EMAIL_OTP has mid-state). Mutable on
 * purpose — each successful factor advances the same session row.
 */
public final class AuthSession {

    private final AuthSessionId id;
    private final Email userEmail;
    private final List<FactorType> factorsPassed;
    private final List<FactorType> factorsRemaining;
    private final LocalDateTime expiresAt;
    private EmailOtpChallenge pendingEmailOtp;

    public AuthSession(AuthSessionId id, Email userEmail, List<FactorType> factorsRemaining, LocalDateTime expiresAt) {
        this.id = Objects.requireNonNull(id);
        this.userEmail = Objects.requireNonNull(userEmail);
        this.factorsRemaining = new ArrayList<>(Objects.requireNonNull(factorsRemaining));
        this.factorsPassed = new ArrayList<>();
        this.expiresAt = Objects.requireNonNull(expiresAt);
    }

    public AuthSessionId id() { return id; }
    public Email userEmail() { return userEmail; }
    public List<FactorType> factorsPassed() { return List.copyOf(factorsPassed); }
    public List<FactorType> factorsRemaining() { return List.copyOf(factorsRemaining); }
    public LocalDateTime expiresAt() { return expiresAt; }
    public Optional<EmailOtpChallenge> pendingEmailOtp() { return Optional.ofNullable(pendingEmailOtp); }

    public boolean isExpired(Clock clock) {
        return !expiresAt.isAfter(LocalDateTime.now(clock));
    }

    public Optional<FactorType> nextFactor() {
        return factorsRemaining.isEmpty() ? Optional.empty() : Optional.of(factorsRemaining.get(0));
    }

    public boolean isComplete() {
        return factorsRemaining.isEmpty();
    }

    public void setEmailOtpChallenge(EmailOtpChallenge challenge) {
        this.pendingEmailOtp = Objects.requireNonNull(challenge);
    }

    public void markFactorPassed(FactorType factor) {
        if (factorsRemaining.isEmpty() || factorsRemaining.get(0) != factor) {
            throw new IllegalStateException("Factor " + factor + " is not the next pending factor");
        }
        factorsRemaining.remove(0);
        factorsPassed.add(factor);
        if (factor == FactorType.EMAIL_OTP) {
            pendingEmailOtp = null;
        }
    }
}

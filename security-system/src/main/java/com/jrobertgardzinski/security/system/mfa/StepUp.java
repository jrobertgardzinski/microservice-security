package com.jrobertgardzinski.security.system.mfa;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.password.domain.HashAlgorithmPort;
import com.jrobertgardzinski.password.domain.PlaintextPassword;
import com.jrobertgardzinski.security.config.mfa.StepUpPolicy;
import com.jrobertgardzinski.security.domain.entity.EnrolledFactor;
import com.jrobertgardzinski.security.domain.repository.EnrolledFactorRepository;
import com.jrobertgardzinski.security.domain.repository.PasswordlessAccountRepository;
import com.jrobertgardzinski.security.domain.repository.UserRepository;
import com.jrobertgardzinski.security.domain.vo.FactorType;

import java.util.List;
import java.util.Optional;

/**
 * Re-proves a caller for a sensitive action. The requirement per action comes from the
 * {@link StepUpPolicy}: {@code NONE} elevates at once; {@code FULL_CHAIN} first re-verifies the
 * password (skipped for a passwordless federated account); then both walk the enrolled factors
 * through the shared {@link MfaChain}. Passing the last factor mints a one-shot
 * {@link SessionElevation} on the caller's access token — the sensitive endpoint consumes it.
 * A caller with no factors and no password step is elevated directly (there is nothing extra to
 * prove beyond the live session).
 */
public class StepUp {

    public sealed interface Result {
        record Elevated() implements Result {}
        record FactorRequired(String ticket, FactorType nextFactor, String challengeData) implements Result {}
        record WrongPassword() implements Result {}
        record WrongProof(int attemptsLeft) implements Result {}
        record TooManyAttempts() implements Result {}
        record InvalidTicket() implements Result {}
    }

    private final StepUpPolicy policy;
    private final UserRepository users;
    private final HashAlgorithmPort hashAlgorithm;
    private final PasswordlessAccountRepository passwordless;
    private final EnrolledFactorRepository factors;
    private final MfaChain chain;
    private final StepUpStore store;
    private final SessionElevation elevation;

    public StepUp(StepUpPolicy policy, UserRepository users, HashAlgorithmPort hashAlgorithm,
                  PasswordlessAccountRepository passwordless, EnrolledFactorRepository factors,
                  MfaChain chain, StepUpStore store, SessionElevation elevation) {
        this.policy = policy;
        this.users = users;
        this.hashAlgorithm = hashAlgorithm;
        this.passwordless = passwordless;
        this.factors = factors;
        this.chain = chain;
        this.store = store;
        this.elevation = elevation;
    }

    public Result start(Email email, String action, String accessToken, String passwordAttempt) {
        String requirement = policy.requirementFor(action);
        if (StepUpPolicy.NONE.equals(requirement)) {
            elevation.elevate(accessToken);
            return new Result.Elevated();
        }
        if (StepUpPolicy.FULL_CHAIN.equals(requirement) && !passwordless.isPasswordless(email)
                && !passwordMatches(email, passwordAttempt)) {
            return new Result.WrongPassword();
        }
        List<EnrolledFactor> enrolled = factors.findByUser(email);
        if (enrolled.isEmpty()) {
            elevation.elevate(accessToken);   // nothing further to prove
            return new Result.Elevated();
        }
        PendingAuthentication pending = chain.begin(email, enrolled);
        String ticket = store.open(new StepUpStore.StepUpPending(email, accessToken, pending));
        return new Result.FactorRequired(ticket, enrolled.get(0).type(), pending.challengeData());
    }

    public Result submitFactor(String ticket, String proof) {
        Optional<StepUpStore.StepUpPending> found = store.find(ticket);
        if (found.isEmpty()) {
            return new Result.InvalidTicket();
        }
        StepUpStore.StepUpPending pending = found.get();
        if (!chain.verify(pending.chain(), proof)) {
            PendingAuthentication afterWrong = pending.chain().afterWrongProof();
            if (afterWrong.attemptsLeft() <= 0) {
                store.close(ticket);
                return new Result.TooManyAttempts();
            }
            store.replace(ticket, new StepUpStore.StepUpPending(pending.email(), pending.accessToken(), afterWrong));
            return new Result.WrongProof(afterWrong.attemptsLeft());
        }
        List<EnrolledFactor> tail = pending.chain().tail();
        if (tail.isEmpty()) {
            store.close(ticket);
            elevation.elevate(pending.accessToken());
            return new Result.Elevated();
        }
        PendingAuthentication advanced = chain.advanceTo(pending.chain(), tail);
        store.replace(ticket, new StepUpStore.StepUpPending(pending.email(), pending.accessToken(), advanced));
        return new Result.FactorRequired(ticket, tail.get(0).type(), advanced.challengeData());
    }

    private boolean passwordMatches(Email email, String passwordAttempt) {
        return passwordAttempt != null && users.findBy(email)
                .map(user -> hashAlgorithm.verify(user.passwordHash(), PlaintextPassword.of(passwordAttempt)))
                .orElse(false);
    }
}

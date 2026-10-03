package com.jrobertgardzinski.security.application.mfa;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.password.domain.PlaintextPassword;
import com.jrobertgardzinski.security.domain.vo.FactorType;
import com.jrobertgardzinski.security.domain.vo.IpAddress;
import com.jrobertgardzinski.security.domain.vo.StepUpAction;
import com.jrobertgardzinski.security.system.mfa.StepUp;
import com.jrobertgardzinski.security.system.throttle.SourceThrottle;

/**
 * Step-up: re-proving yourself for a sensitive action. The per-action policy decides whether a
 * password and/or factors are needed; passing elevates the caller's access token for a short
 * window, which the sensitive endpoint then consumes.
 */
public final class StepUpService {

    private final StepUp stepUp;
    private final SourceThrottle throttle;

    public StepUpService(StepUp stepUp, SourceThrottle throttle) {
        this.stepUp = stepUp;
        this.throttle = throttle;
    }

    /**
     * Begins a step-up for {@code action}. {@code caller} is null only when no caller was published
     * — which should not happen behind the authorization filter, and is then counted against the
     * {@code source} and refused.
     */
    public Outcome start(Email caller, IpAddress source, String action, String accessToken, String password) {
        String subject = subjectOf(caller, source);
        SourceThrottle.Decision decision = throttle.check(subject);
        if (!decision.allowed()) {
            return new Outcome.Throttled(decision.retryAfterSeconds());
        }
        if (caller == null) {
            return new Outcome.NotAuthenticated();
        }
        // the action must be one of the catalogue: an unknown name is a client error, not an
        // elevation minted for something nobody would ever consume
        java.util.Optional<StepUpAction> known = StepUpAction.fromWire(action == null ? "" : action);
        if (known.isEmpty()) {
            return new Outcome.UnknownAction();
        }
        // a BLANK password is no password: it used to reach the value object and answer 500 with
        // its rule, where an absent one has always answered "wrong password" — one situation, one
        // answer, and the use case already knows what to do with nothing
        PlaintextPassword attempt = password == null || password.isBlank() ? null : PlaintextPassword.of(password);
        return outcome(stepUp.start(caller, known.get(), accessToken, attempt), subject);
    }

    /** The next proof against a step-up's ticket. */
    public Outcome submitFactor(Email caller, IpAddress source, String ticket, String proof) {
        String subject = subjectOf(caller, source);
        SourceThrottle.Decision decision = throttle.check(subject);
        if (!decision.allowed()) {
            return new Outcome.Throttled(decision.retryAfterSeconds());
        }
        if (ticket == null || ticket.isBlank() || proof == null || proof.isBlank()) {
            return new Outcome.Incomplete();
        }
        return outcome(stepUp.submitFactor(ticket, proof), subject);
    }

    /**
     * Who the throttle's window belongs to. Both steps are authenticated, so the caller's own
     * identity is known and is the honest key: on the address, one NAT shares one budget and a
     * colleague's impatience locks everyone else out. The address is kept as the fallback for the
     * case that should not happen — no published caller — because a limit that silently stops
     * limiting is worse than one keyed coarsely.
     */
    private static String subjectOf(Email caller, IpAddress source) {
        return caller != null ? "caller:" + caller.value() : "source:" + source.value();
    }

    private Outcome outcome(StepUp.Result result, String subject) {
        return switch (result) {
            case StepUp.Result.Elevated elevated -> {
                // a caller who has just re-proved themselves is not the volume this throttle defends
                // against — same rule the brute-force guard follows on a correct password
                throttle.forgive(subject);
                yield new Outcome.Elevated();
            }
            case StepUp.Result.FactorRequired next ->
                    new Outcome.FactorRequired(next.ticket(), next.nextFactor(), next.challengeData());
            case StepUp.Result.WrongPassword wrong -> new Outcome.WrongPassword();
            case StepUp.Result.WrongProof wrong -> new Outcome.WrongProof(wrong.attemptsLeft());
            case StepUp.Result.TooManyAttempts tooMany -> new Outcome.TooManyAttempts();
            case StepUp.Result.InvalidTicket invalid -> new Outcome.InvalidTicket();
            case StepUp.Result.NothingToProveWith nothing -> new Outcome.NothingToProveWith();
        };
    }

    public sealed interface Outcome {

        record Elevated() implements Outcome {}

        record FactorRequired(String ticket, FactorType nextFactor, String challengeData) implements Outcome {}

        record WrongPassword() implements Outcome {}

        record WrongProof(int attemptsLeft) implements Outcome {}

        record TooManyAttempts() implements Outcome {}

        record InvalidTicket() implements Outcome {}

        /**
         * The caller is who they say they are, but the account carries nothing to re-prove with;
         * the way out is to enrol a factor, which is a SECOND_FACTORS action and still reachable.
         */
        record NothingToProveWith() implements Outcome {}

        record UnknownAction() implements Outcome {}

        /** The ticket or the proof is missing or blank. */
        record Incomplete() implements Outcome {}

        record NotAuthenticated() implements Outcome {}

        record Throttled(long retryAfterSeconds) implements Outcome {}
    }
}

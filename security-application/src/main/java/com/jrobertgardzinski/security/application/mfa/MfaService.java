package com.jrobertgardzinski.security.application.mfa;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.application.TransactionBoundary;
import com.jrobertgardzinski.security.domain.session.SessionTokens;
import com.jrobertgardzinski.security.domain.core.User;
import com.jrobertgardzinski.security.domain.mfa.EnrolledFactorRepository;
import com.jrobertgardzinski.security.domain.mfa.RecoveryCodeRepository;
import com.jrobertgardzinski.security.domain.core.UserRepository;
import com.jrobertgardzinski.security.domain.mfa.FactorType;
import com.jrobertgardzinski.security.domain.core.IpAddress;
import com.jrobertgardzinski.security.domain.core.Role;
import com.jrobertgardzinski.security.system.authentication.ContinueAuthentication;
import com.jrobertgardzinski.security.system.authentication.ContinueAuthenticationResult;
import com.jrobertgardzinski.security.system.mfa.EnrolFactor;
import com.jrobertgardzinski.security.system.mfa.FactorRegistry;
import com.jrobertgardzinski.security.system.mfa.GenerateRecoveryCodes;
import com.jrobertgardzinski.security.system.mfa.MfaCompliance;
import com.jrobertgardzinski.security.system.core.SourceThrottle;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * Second factors: finishing a sign-in with them, enrolling and removing one's own, recovery codes,
 * and an administrator's reset of somebody else's.
 *
 * <p>Several of these are guarded by a step-up, which only the infrastructure can check (it reads
 * the access token). It is handed in as a {@link BooleanSupplier} and asked LAST, after everything
 * that could refuse the request on its own: asking SPENDS a one-shot elevation, and a typo used to
 * cost the whole step-up chain and then answer 400 (HTTP-10).
 */
public final class MfaService {

    private final ContinueAuthentication continueAuthentication;
    private final EnrolFactor enrolFactor;
    private final EnrolledFactorRepository enrolledFactors;
    private final FactorRegistry registry;
    private final UserRepository users;
    private final MfaCompliance compliance;
    private final GenerateRecoveryCodes generateRecoveryCodes;
    private final RecoveryCodeRepository recoveryCodes;
    private final SourceThrottle authenticationThrottle;
    private final TransactionBoundary transactionBoundary;

    public MfaService(ContinueAuthentication continueAuthentication, EnrolFactor enrolFactor,
                      EnrolledFactorRepository enrolledFactors, FactorRegistry registry, UserRepository users,
                      MfaCompliance compliance, GenerateRecoveryCodes generateRecoveryCodes,
                      RecoveryCodeRepository recoveryCodes, SourceThrottle authenticationThrottle,
                      TransactionBoundary transactionBoundary) {
        this.continueAuthentication = continueAuthentication;
        this.enrolFactor = enrolFactor;
        this.enrolledFactors = enrolledFactors;
        this.registry = registry;
        this.users = users;
        this.compliance = compliance;
        this.generateRecoveryCodes = generateRecoveryCodes;
        this.recoveryCodes = recoveryCodes;
        this.authenticationThrottle = authenticationThrottle;
        this.transactionBoundary = transactionBoundary;
    }

    /**
     * The next proof against a sign-in's ticket. Per ticket the attempts are capped, but tickets are
     * not: somebody holding the password can open one after another and buy five guesses at the
     * second factor each time — and every ticket for a code factor mails the victim another code.
     * So this shares one per-source window with the password sign-in itself: the loop is bounded by
     * the source, whichever half of it is being spun.
     */
    public SignIn continueSignIn(String ticket, String proof, IpAddress source) {
        SourceThrottle.Decision decision = authenticationThrottle.check(source);
        if (!decision.allowed()) {
            return new SignIn.Throttled(decision.retryAfterSeconds());
        }
        if (blank(ticket) || blank(proof)) {
            return new SignIn.Incomplete();
        }
        return switch (transactionBoundary.execute(() -> continueAuthentication.execute(ticket, proof))) {
            case ContinueAuthenticationResult.Completed completed -> new SignIn.Completed(completed.session());
            case ContinueAuthenticationResult.NextFactor next ->
                    new SignIn.NextFactor(ticket, next.type(), next.challengeData());
            case ContinueAuthenticationResult.WrongProof wrong -> new SignIn.WrongProof(wrong.attemptsLeft());
            case ContinueAuthenticationResult.TooManyAttempts tooMany -> new SignIn.TooManyAttempts();
            case ContinueAuthenticationResult.InvalidTicket invalid -> new SignIn.InvalidTicket();
        };
    }

    public Factors factors(Email caller) {
        return new Factors(
                enrolledFactors.findByUser(caller).stream().map(f -> new Held(f.type(), f.label())).toList(),
                registry.offered().stream().sorted(Comparator.comparing(FactorType::value)).toList());
    }

    /**
     * Starts enrolling a factor. Enrolling rewrites the sign-in chain, so a merely-live (possibly
     * stolen) session must step up first — otherwise a thief adds an attacker-held factor and locks
     * the owner out. Guarding the start alone is enough: a confirmation needs a pending enrolment
     * only a guarded start mints.
     */
    public Enrolment startEnrolment(Email caller, String type, String target, BooleanSupplier stepUp) {
        FactorType factorType;
        try {
            factorType = FactorType.of(type);
        } catch (IllegalArgumentException unknown) {
            return new Enrolment.UnknownFactor();
        }
        if (!stepUp.getAsBoolean()) {
            return new Enrolment.StepUpRequired();
        }
        // the e-mail factor's code always goes to the caller's OWN already-verified address — never a
        // target from the body, or a thief would point the codes at their own inbox
        String sendTo = "EMAIL_CODE".equals(factorType.value()) || target == null ? caller.value() : target;
        return enrolment(transactionBoundary.execute(() -> enrolFactor.start(caller, factorType, sendTo)));
    }

    public Enrolment confirmEnrolment(Email caller, String type, String proof) {
        FactorType factorType;
        try {
            factorType = FactorType.of(type);
        } catch (IllegalArgumentException unknown) {
            return new Enrolment.UnknownFactor();
        }
        return enrolment(transactionBoundary.execute(() -> enrolFactor.confirm(caller, factorType, proof)));
    }

    /** Drops one of the caller's factors; that weakens the account, so it takes a step-up. */
    public Removal removeFactor(Email caller, String type, BooleanSupplier stepUp) {
        FactorType factorType;
        try {
            factorType = FactorType.of(type);
        } catch (IllegalArgumentException unknown) {
            return new Removal.UnknownFactor();
        }
        Set<Role> roles = users.findBy(caller).map(User::roles).orElse(Set.of(Role.USER));
        // the floor is answered BEFORE the step-up as well as inside it: "you cannot do this at
        // all" is a better answer than "prove yourself again, and then you still cannot" — and it
        // costs no elevation to say
        if (compliance.removalWouldBreakFloor(caller, roles)) {
            return new Removal.WouldBreakFloor();
        }
        if (!stepUp.getAsBoolean()) {
            return new Removal.StepUpRequired();
        }
        // and again INSIDE the transaction that removes. The check used to happen only outside it,
        // so two removals racing each other both read "one left over the floor" and both removed;
        // here the second sees what the first wrote. (Two transactions that START together can
        // still both read the old count under READ COMMITTED — closing that needs the account's own
        // lock, as the brute-force guard takes one: the same shape of race, far less reachable.)
        return transactionBoundary.execute(() -> {
            if (compliance.removalWouldBreakFloor(caller, roles)) {
                return new Removal.WouldBreakFloor();
            }
            enrolledFactors.remove(caller, factorType);
            return new Removal.Removed();
        });
    }

    /**
     * A fresh batch of recovery codes, the previous one dead. They are spare keys — shown once, and
     * each one signs in when a factor is out of reach — so a merely-live session must not be able
     * to mint itself a set and keep them.
     */
    public RecoveryCodes generateRecoveryCodes(Email caller, BooleanSupplier stepUp) {
        if (!stepUp.getAsBoolean()) {
            return new RecoveryCodes.StepUpRequired();
        }
        return new RecoveryCodes.Generated(transactionBoundary.execute(() -> generateRecoveryCodes.execute(caller)));
    }

    public int unusedRecoveryCodes(Email caller) {
        return recoveryCodes.unusedCount(caller);
    }

    /**
     * An administrator wipes another user's factors — the way back for somebody locked out of every
     * one. That drops the user below their role's floor and forces re-enrolment, so it takes a
     * step-up on top of the role, which the caller has already been checked for.
     */
    public AdminReset resetFactors(String user, BooleanSupplier stepUp) {
        if (!stepUp.getAsBoolean()) {
            return new AdminReset.StepUpRequired();
        }
        Email address;
        try {
            address = Email.of(user);
        } catch (IllegalArgumentException invalid) {
            return new AdminReset.InvalidEmail();
        }
        transactionBoundary.execute(() -> {
            enrolledFactors.removeAll(address);
            return null;
        });
        return new AdminReset.Reset();
    }

    private static Enrolment enrolment(EnrolFactor.Result result) {
        return switch (result) {
            case EnrolFactor.Result.Started started -> new Enrolment.Started(started.display());
            case EnrolFactor.Result.Enrolled enrolled -> new Enrolment.Enrolled(enrolled.type());
            case EnrolFactor.Result.WrongProof wrong -> new Enrolment.WrongProof();
            case EnrolFactor.Result.NoPendingEnrolment none -> new Enrolment.NoPendingEnrolment();
            case EnrolFactor.Result.UnsupportedFactor unsupported -> new Enrolment.UnsupportedFactor();
        };
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public sealed interface SignIn {

        record Completed(SessionTokens session) implements SignIn {}

        record NextFactor(String ticket, FactorType type, String challengeData) implements SignIn {}

        record WrongProof(int attemptsLeft) implements SignIn {}

        record TooManyAttempts() implements SignIn {}

        record InvalidTicket() implements SignIn {}

        /** The ticket or the proof is missing or blank. */
        record Incomplete() implements SignIn {}

        record Throttled(long retryAfterSeconds) implements SignIn {}
    }

    /** A factor the caller holds — what it is and what they called it, never its secret. */
    public record Held(FactorType type, String label) {}

    public record Factors(List<Held> have, List<FactorType> offered) {}

    public sealed interface Enrolment {

        /** A code factor sent a code ({@code display} null); a possession factor returns what to show. */
        record Started(String display) implements Enrolment {}

        record Enrolled(FactorType type) implements Enrolment {}

        record WrongProof() implements Enrolment {}

        record NoPendingEnrolment() implements Enrolment {}

        /** A factor type that exists, but that this deployment does not offer. */
        record UnsupportedFactor() implements Enrolment {}

        /** Not a factor type at all. */
        record UnknownFactor() implements Enrolment {}

        record StepUpRequired() implements Enrolment {}
    }

    public sealed interface Removal {

        record Removed() implements Removal {}

        /** Removing it would leave the caller under the factor count their role demands. */
        record WouldBreakFloor() implements Removal {}

        record UnknownFactor() implements Removal {}

        record StepUpRequired() implements Removal {}
    }

    public sealed interface RecoveryCodes {

        /** The plain codes: in this one answer and never again, only their hashes are kept. */
        record Generated(List<String> codes) implements RecoveryCodes {}

        record StepUpRequired() implements RecoveryCodes {}
    }

    public sealed interface AdminReset {

        record Reset() implements AdminReset {}

        record InvalidEmail() implements AdminReset {}

        record StepUpRequired() implements AdminReset {}
    }
}

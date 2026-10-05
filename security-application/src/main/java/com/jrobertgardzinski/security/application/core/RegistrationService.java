package com.jrobertgardzinski.security.application.core;

import com.jrobertgardzinski.email.config.CanRegisterConfig;
import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.password.domain.PlaintextPassword;
import com.jrobertgardzinski.password.policy.PasswordPolicy;
import com.jrobertgardzinski.security.application.TransactionBoundary;
import com.jrobertgardzinski.security.domain.core.RegistrationNoticeNotifier;
import com.jrobertgardzinski.security.domain.mailbox.EmailVerificationRepository;
import com.jrobertgardzinski.security.domain.core.IpAddress;
import com.jrobertgardzinski.security.system.core.EmailErrorCodes;
import com.jrobertgardzinski.security.system.core.PasswordErrorCodes;
import com.jrobertgardzinski.security.system.core.Register;
import com.jrobertgardzinski.security.system.core.RegisterResult;
import com.jrobertgardzinski.security.system.core.SourceThrottle;
import com.jrobertgardzinski.security.system.mailbox.RequestEmailVerification;

/**
 * Registration, from the strings a caller typed to the mail that makes the account usable.
 *
 * <p>{@link Register} works on the domain; this is where a {@code String} becomes an {@link Email}
 * and a {@link PlaintextPassword}, and where what follows the use case is decided: a fresh account
 * is sent a verification link, a taken address is told by mail, and the caller is never told which.
 */
public final class RegistrationService {

    private final Register register;
    private final RequestEmailVerification requestEmailVerification;
    private final EmailVerificationRepository emailVerifications;
    private final RegistrationNoticeNotifier registrationNoticeNotifier;
    private final SourceThrottle throttle;
    private final TransactionBoundary transactionBoundary;

    public RegistrationService(Register register, RequestEmailVerification requestEmailVerification,
                               EmailVerificationRepository emailVerifications,
                               RegistrationNoticeNotifier registrationNoticeNotifier,
                               SourceThrottle throttle, TransactionBoundary transactionBoundary) {
        this.register = register;
        this.requestEmailVerification = requestEmailVerification;
        this.emailVerifications = emailVerifications;
        this.registrationNoticeNotifier = registrationNoticeNotifier;
        this.throttle = throttle;
        this.transactionBoundary = transactionBoundary;
    }

    public Outcome register(String email, String password, IpAddress source) {
        // guard before the expensive work: registration hashes a password and creates an account
        SourceThrottle.Decision decision = throttle.check(source);
        if (!decision.allowed()) {
            return new Outcome.Throttled(decision.retryAfterSeconds());
        }

        // The account and the mail that makes it usable are written in ONE transaction. They used to
        // be two: the account committed first, and anything that went wrong afterwards — the JVM
        // being stopped between them, a failure appending to the outbox — left an account whose
        // owner was never sent a link, while every sign-in demands a verified address. Nothing here
        // talks to the mail service synchronously (the notifier appends to the transactional
        // outbox), so there is no slow call to keep out of the transaction.
        RegisterResult result = transactionBoundary.execute(() -> {
            RegisterResult outcome = register.execute(() -> Email.of(email), () -> PlaintextPassword.of(password));
            switch (outcome) {
                // sign-in requires a verified address, so onboarding starts the verification here
                case RegisterResult.Registered registered -> requestEmailVerification.execute(registered.user().email());
                // quiet refusal: the caller sees a fresh-looking registration; the address owner
                // is told by mail — a lost-mail re-register gets a fresh link, a real account a notice
                case RegisterResult.EmailAlreadyTaken alreadyTaken -> {
                    if (emailVerifications.isVerified(alreadyTaken.email())) {
                        registrationNoticeNotifier.sendAlreadyRegistered(alreadyTaken.email());
                    } else {
                        requestEmailVerification.execute(alreadyTaken.email());
                    }
                }
                case RegisterResult.Rejected rejected -> {
                    // nothing was written, and nothing is mailed: the address may not even be one
                }
            }
            return outcome;
        });

        return switch (result) {
            case RegisterResult.Registered registered -> new Outcome.Registered();
            case RegisterResult.EmailAlreadyTaken alreadyTaken -> new Outcome.QuietlyRefused();
            case RegisterResult.Rejected rejected -> new Outcome.Rejected(
                    rejected.emailErrors(), rejected.emailPolicy(),
                    rejected.passwordErrors(), rejected.passwordPolicy());
        };
    }

    public sealed interface Outcome {

        /** A new account; its verification link is on its way. */
        record Registered() implements Outcome {}

        /**
         * The address already holds an account. The caller must not be able to tell this from
         * {@link Registered} (anti-enumeration) — the mail decides, readable only by the owner.
         */
        record QuietlyRefused() implements Outcome {}

        /** One or both values broke a rule; each carries the policy in force for this attempt. */
        record Rejected(EmailErrorCodes emailErrors, CanRegisterConfig emailPolicy,
                        PasswordErrorCodes passwordErrors, PasswordPolicy passwordPolicy) implements Outcome {}

        /** Too many registrations from this source; nothing was hashed or written. */
        record Throttled(long retryAfterSeconds) implements Outcome {}
    }
}

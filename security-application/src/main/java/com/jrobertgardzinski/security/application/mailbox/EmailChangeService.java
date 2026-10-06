package com.jrobertgardzinski.security.application.mailbox;

import com.jrobertgardzinski.email.config.CanRegisterConfig;
import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.application.TransactionBoundary;
import com.jrobertgardzinski.security.domain.core.RegistrationNoticeNotifier;
import com.jrobertgardzinski.security.domain.mailbox.VerificationToken;
import com.jrobertgardzinski.security.system.mailbox.ConfirmEmailChange;
import com.jrobertgardzinski.security.system.mailbox.ConfirmEmailChangeResult;
import com.jrobertgardzinski.security.system.mailbox.RequestEmailChange;
import com.jrobertgardzinski.security.system.mailbox.RequestEmailChangeResult;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Moving an account to a new address: asking for the change, and following the link mailed to the
 * new address. Moving the address moves the account, so the request takes a step-up.
 */
public final class EmailChangeService {

    private final RequestEmailChange requestEmailChange;
    private final ConfirmEmailChange confirmEmailChange;
    private final RegistrationNoticeNotifier noticeNotifier;
    private final TransactionBoundary transactionBoundary;

    public EmailChangeService(RequestEmailChange requestEmailChange, ConfirmEmailChange confirmEmailChange,
                              RegistrationNoticeNotifier noticeNotifier, TransactionBoundary transactionBoundary) {
        this.requestEmailChange = requestEmailChange;
        this.confirmEmailChange = confirmEmailChange;
        this.noticeNotifier = noticeNotifier;
        this.transactionBoundary = transactionBoundary;
    }

    /**
     * Starts moving the account to {@code newEmail}. Moving the address moves the account, and the
     * confirmation lands in the NEW mailbox — so the caller must have stepped up. The address is
     * read BEFORE {@code stepUp} is asked, because asking SPENDS a one-shot elevation: a typo in the
     * new address used to cost the whole step-up chain and then answer 400 (HTTP-10).
     */
    public EmailChange requestEmailChange(Email caller, String newEmail, BooleanSupplier stepUp) {
        Email address;
        try {
            address = Email.of(newEmail);
        } catch (IllegalArgumentException invalid) {
            return new EmailChange.InvalidEmail();
        }
        if (!stepUp.getAsBoolean()) {
            return new EmailChange.StepUpRequired();
        }
        return switch (transactionBoundary.execute(() -> requestEmailChange.execute(caller, address))) {
            case RequestEmailChangeResult.Requested requested -> new EmailChange.LinkSent();
            // the policy's refusal is NOT quiet: it is about the address the caller typed, not about
            // who else holds it, so it says which rule was broken — exactly as registration does
            case RequestEmailChangeResult.Rejected rejected ->
                    new EmailChange.Rejected(rejected.emailErrors(), rejected.emailPolicy());
            case RequestEmailChangeResult.EmailTaken taken -> {
                // quiet refusal: the caller sees a fresh request; the address owner is told by mail
                transactionBoundary.execute(() -> {
                    noticeNotifier.sendAlreadyRegistered(address);
                    return null;
                });
                yield new EmailChange.LinkSent();
            }
        };
    }

    /** Follows the link mailed to the new address; whoever follows it need not be signed in. */
    public EmailConfirmation confirmEmailChange(String token) {
        VerificationToken verificationToken;
        try {
            verificationToken = new VerificationToken(token);
        } catch (IllegalArgumentException missingOrBlank) {
            return new EmailConfirmation.InvalidToken();
        }
        // nobody else is told: the rest of the estate keys a person's rows on their id, and shows
        // the address by asking this service
        return switch (transactionBoundary.execute(() -> confirmEmailChange.execute(verificationToken))) {
            case ConfirmEmailChangeResult.EmailChanged changed -> new EmailConfirmation.EmailChanged(changed.newEmail());
            case ConfirmEmailChangeResult.EmailTaken taken -> new EmailConfirmation.EmailTaken();
            case ConfirmEmailChangeResult.InvalidToken invalid -> new EmailConfirmation.InvalidToken();
        };
    }

    public sealed interface EmailChange {

        /** Said too when the address is taken: its owner is told by mail, the caller is not. */
        record LinkSent() implements EmailChange {}

        record Rejected(List<String> emailErrors, CanRegisterConfig emailPolicy) implements EmailChange {}

        record InvalidEmail() implements EmailChange {}

        record StepUpRequired() implements EmailChange {}
    }

    public sealed interface EmailConfirmation {

        record EmailChanged(Email newEmail) implements EmailConfirmation {}

        /**
         * The token was good, but the address was taken in the meantime — the world moved, and
         * fetching another token would fail exactly the same way.
         */
        record EmailTaken() implements EmailConfirmation {}

        record InvalidToken() implements EmailConfirmation {}
    }
}

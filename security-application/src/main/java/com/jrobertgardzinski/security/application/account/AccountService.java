package com.jrobertgardzinski.security.application.account;

import com.jrobertgardzinski.email.config.CanRegisterConfig;
import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.password.domain.PlaintextPassword;
import com.jrobertgardzinski.security.application.TransactionBoundary;
import com.jrobertgardzinski.security.domain.core.RegistrationNoticeNotifier;
import com.jrobertgardzinski.security.domain.core.IpAddress;
import com.jrobertgardzinski.security.domain.mailbox.VerificationToken;
import com.jrobertgardzinski.security.system.account.ChangePassword;
import com.jrobertgardzinski.security.system.account.ChangePasswordResult;
import com.jrobertgardzinski.security.system.mailbox.ConfirmEmailChange;
import com.jrobertgardzinski.security.system.mailbox.ConfirmEmailChangeResult;
import com.jrobertgardzinski.security.system.mailbox.RequestEmailChange;
import com.jrobertgardzinski.security.system.mailbox.RequestEmailChangeResult;
import com.jrobertgardzinski.security.system.core.SourceThrottle;

import java.util.List;
import java.util.function.BooleanSupplier;

/** What a signed-in user changes about their own account: the password and the address. */
public final class AccountService {

    private final ChangePassword changePassword;
    private final RequestEmailChange requestEmailChange;
    private final ConfirmEmailChange confirmEmailChange;
    private final RegistrationNoticeNotifier noticeNotifier;
    private final SourceThrottle changePasswordThrottle;
    private final TransactionBoundary transactionBoundary;

    public AccountService(ChangePassword changePassword, RequestEmailChange requestEmailChange,
                          ConfirmEmailChange confirmEmailChange, RegistrationNoticeNotifier noticeNotifier,
                          SourceThrottle changePasswordThrottle, TransactionBoundary transactionBoundary) {
        this.changePassword = changePassword;
        this.requestEmailChange = requestEmailChange;
        this.confirmEmailChange = confirmEmailChange;
        this.noticeNotifier = noticeNotifier;
        this.changePasswordThrottle = changePasswordThrottle;
        this.transactionBoundary = transactionBoundary;
    }

    public PasswordChange changePassword(Email caller, String currentPassword, String newPassword, IpAddress source) {
        // changing a password verifies the CURRENT one with a full hash: unthrottled it is a password
        // oracle for whoever holds a live (possibly stolen) access token
        SourceThrottle.Decision decision = changePasswordThrottle.check(source);
        if (!decision.allowed()) {
            return new PasswordChange.Throttled(decision.retryAfterSeconds());
        }
        if (blank(currentPassword) || blank(newPassword)) {
            return new PasswordChange.Incomplete();
        }
        return switch (transactionBoundary.execute(() -> changePassword.execute(
                caller, () -> PlaintextPassword.of(currentPassword), () -> PlaintextPassword.of(newPassword)))) {
            case ChangePasswordResult.Changed changed -> new PasswordChange.Changed();
            case ChangePasswordResult.WrongCurrentPassword wrong -> new PasswordChange.WrongCurrentPassword();
            case ChangePasswordResult.WeakPassword weak -> new PasswordChange.WeakPassword();
        };
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

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public sealed interface PasswordChange {

        record Changed() implements PasswordChange {}

        record WrongCurrentPassword() implements PasswordChange {}

        record WeakPassword() implements PasswordChange {}

        /** One of the two passwords is missing or blank. */
        record Incomplete() implements PasswordChange {}

        record Throttled(long retryAfterSeconds) implements PasswordChange {}
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

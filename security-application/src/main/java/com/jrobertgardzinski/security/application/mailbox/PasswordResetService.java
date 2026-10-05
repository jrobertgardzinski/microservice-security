package com.jrobertgardzinski.security.application.mailbox;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.email.domain.InvalidEmailException;
import com.jrobertgardzinski.password.domain.PlaintextPassword;
import com.jrobertgardzinski.security.application.TransactionBoundary;
import com.jrobertgardzinski.security.domain.core.IpAddress;
import com.jrobertgardzinski.security.domain.mailbox.PasswordResetToken;
import com.jrobertgardzinski.security.system.mailbox.RequestPasswordReset;
import com.jrobertgardzinski.security.system.mailbox.ResetPassword;
import com.jrobertgardzinski.security.system.mailbox.ResetPasswordResult;
import com.jrobertgardzinski.security.system.core.SourceThrottle;

/** A forgotten password: asking for a reset link, and setting a new password with it. */
public final class PasswordResetService {

    private final RequestPasswordReset requestPasswordReset;
    private final ResetPassword resetPassword;
    private final SourceThrottle throttle;
    private final TransactionBoundary transactionBoundary;

    public PasswordResetService(RequestPasswordReset requestPasswordReset, ResetPassword resetPassword,
                                SourceThrottle throttle, TransactionBoundary transactionBoundary) {
        this.requestPasswordReset = requestPasswordReset;
        this.resetPassword = resetPassword;
        this.throttle = throttle;
        this.transactionBoundary = transactionBoundary;
    }

    public Request requestLink(String email, IpAddress source) {
        SourceThrottle.Decision decision = throttle.check(source);
        if (!decision.allowed()) {
            return new Request.Throttled(decision.retryAfterSeconds());
        }
        Email address;
        try {
            address = Email.of(email);
        } catch (InvalidEmailException malformed) {
            // This answers the same whether or not the address has an account, so that nobody can
            // probe who is registered here. A malformed address must be just as quiet: a different
            // answer for it would be a different answer for SOME inputs, and it used to be the
            // loudest one of all — a 500 quoting the domain back at the caller.
            return new Request.LinkSent();
        }
        transactionBoundary.execute(() -> {
            requestPasswordReset.execute(address);
            return null;
        });
        return new Request.LinkSent();
    }

    public Reset reset(String token, String newPassword) {
        PasswordResetToken resetToken;
        try {
            resetToken = new PasswordResetToken(token);
        } catch (IllegalArgumentException missingOrBlank) {
            // a token that cannot even be constructed is simply not a valid token
            return new Reset.InvalidToken();
        }
        return switch (transactionBoundary.execute(
                () -> resetPassword.execute(resetToken, () -> PlaintextPassword.of(newPassword)))) {
            case ResetPasswordResult.PasswordReset reset -> new Reset.PasswordReset(reset.email());
            case ResetPasswordResult.WeakPassword weak -> new Reset.WeakPassword();
            case ResetPasswordResult.InvalidToken invalid -> new Reset.InvalidToken();
        };
    }

    public sealed interface Request {

        /** Said whether a link went out or not: the answer never tells who is registered. */
        record LinkSent() implements Request {}

        record Throttled(long retryAfterSeconds) implements Request {}
    }

    public sealed interface Reset {

        record PasswordReset(Email email) implements Reset {}

        record WeakPassword() implements Reset {}

        record InvalidToken() implements Reset {}
    }
}

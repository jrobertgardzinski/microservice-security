package com.jrobertgardzinski.security.application.verification;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.email.domain.InvalidEmailException;
import com.jrobertgardzinski.security.application.TransactionBoundary;
import com.jrobertgardzinski.security.domain.vo.IpAddress;
import com.jrobertgardzinski.security.domain.vo.token.VerificationToken;
import com.jrobertgardzinski.security.system.throttle.SourceThrottle;
import com.jrobertgardzinski.security.system.verification.RequestEmailVerification;
import com.jrobertgardzinski.security.system.verification.VerifyEmail;
import com.jrobertgardzinski.security.system.verification.VerifyEmailResult;

/** Proving an address: asking for a link, and following one. */
public final class VerificationService {

    private final RequestEmailVerification requestEmailVerification;
    private final VerifyEmail verifyEmail;
    private final SourceThrottle throttle;
    private final TransactionBoundary transactionBoundary;

    public VerificationService(RequestEmailVerification requestEmailVerification, VerifyEmail verifyEmail,
                               SourceThrottle throttle, TransactionBoundary transactionBoundary) {
        this.requestEmailVerification = requestEmailVerification;
        this.verifyEmail = verifyEmail;
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
            requestEmailVerification.execute(address);
            return null;
        });
        return new Request.LinkSent();
    }

    public Verify verify(String token) {
        VerificationToken verificationToken;
        try {
            verificationToken = new VerificationToken(token);
        } catch (IllegalArgumentException missingOrBlank) {
            // a token that cannot even be constructed is simply not a valid token
            return new Verify.InvalidToken();
        }
        return switch (transactionBoundary.execute(() -> verifyEmail.execute(verificationToken))) {
            case VerifyEmailResult.Verified verified -> new Verify.Verified(verified.email());
            case VerifyEmailResult.Rejected rejected -> new Verify.InvalidToken();
        };
    }

    public sealed interface Request {

        /** Said whether a link went out or not: the answer never tells who is registered. */
        record LinkSent() implements Request {}

        record Throttled(long retryAfterSeconds) implements Request {}
    }

    public sealed interface Verify {

        record Verified(Email email) implements Verify {}

        record InvalidToken() implements Verify {}
    }
}

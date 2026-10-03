package com.jrobertgardzinski.security.application.authentication;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.password.domain.PlaintextPassword;
import com.jrobertgardzinski.security.application.TransactionBoundary;
import com.jrobertgardzinski.security.domain.entity.SessionTokens;
import com.jrobertgardzinski.security.domain.vo.AuthenticationRequest;
import com.jrobertgardzinski.security.domain.vo.FactorType;
import com.jrobertgardzinski.security.domain.vo.IpAddress;
import com.jrobertgardzinski.security.domain.vo.Source;
import com.jrobertgardzinski.security.system.authentication.Authentication;
import com.jrobertgardzinski.security.system.authentication.AuthenticationResult;
import com.jrobertgardzinski.security.system.throttle.SourceThrottle;

import java.time.LocalDateTime;

/**
 * Password sign-in, from the strings a caller typed to a session — or to the first factor of a
 * chain. The source a brute-force guard keys on is the caller's address, never something the
 * caller may choose; the user agent rides along as observed context, forensics only.
 */
public final class AuthenticationService {

    private final Authentication authentication;
    private final SourceThrottle throttle;
    private final TransactionBoundary transactionBoundary;

    public AuthenticationService(Authentication authentication, SourceThrottle throttle,
                                 TransactionBoundary transactionBoundary) {
        this.authentication = authentication;
        this.throttle = throttle;
        this.transactionBoundary = transactionBoundary;
    }

    public Outcome authenticate(String email, String password, IpAddress ip, String userAgent) {
        // the per-account guard counts FAILURES and a correct password clears them, so it does not
        // bound how many attempts one source may start — this does
        SourceThrottle.Decision decision = throttle.check(ip);
        if (!decision.allowed()) {
            return new Outcome.Throttled(decision.retryAfterSeconds());
        }
        AuthenticationRequest request;
        try {
            request = new AuthenticationRequest(new Source(ip, userAgent), Email.of(email), PlaintextPassword.of(password));
        } catch (IllegalArgumentException unreadable) {
            // A credential the domain cannot even construct used to escape as a 500 carrying the
            // domain's own sentence: an Internal Server Error for a typo, plus a stack trace in the
            // log for every one of them. To the caller it is not a different KIND of failure — an
            // address that cannot exist owns no account — so it answers exactly like a wrong
            // password, and learns nothing a rejection did not already say. It costs no lookup and
            // therefore no brute-force count: there is no account here to guess at.
            return new Outcome.Unreadable();
        }

        return switch (transactionBoundary.execute(() -> authentication.execute(request))) {
            case AuthenticationResult.Authenticated authenticated -> new Outcome.Authenticated(authenticated.session());
            case AuthenticationResult.Rejected rejected -> new Outcome.Rejected();
            case AuthenticationResult.EmailNotVerified notVerified -> new Outcome.EmailNotVerified();
            case AuthenticationResult.Blocked blocked -> new Outcome.Blocked(blocked.authenticationBlock().expiryDate());
            case AuthenticationResult.MfaRequired mfa ->
                    new Outcome.MfaRequired(mfa.ticket(), mfa.nextFactor(), mfa.challengeData());
        };
    }

    public sealed interface Outcome {

        record Authenticated(SessionTokens session) implements Outcome {}

        /** Wrong credentials, or no such account — the caller is not told which. */
        record Rejected() implements Outcome {}

        /**
         * A credential the domain cannot construct. Kept apart from {@link Rejected} because it was
         * never counted as a failure; to the caller it is the same refusal.
         */
        record Unreadable() implements Outcome {}

        /** Correct credentials, but the address awaits verification. */
        record EmailNotVerified() implements Outcome {}

        /** The account's brute-force guard tripped; nothing is checked until {@code expiry}. */
        record Blocked(LocalDateTime expiry) implements Outcome {}

        /** The password was right, but the user has factors: no session yet, the first challenge is out. */
        record MfaRequired(String ticket, FactorType nextFactor, String challengeData) implements Outcome {}

        /** Too many attempts from this source, whatever accounts they were at. */
        record Throttled(long retryAfterSeconds) implements Outcome {}
    }
}

package com.jrobertgardzinski.security.system.authentication;

import com.jrobertgardzinski.security.domain.entity.SessionTokens;
import com.jrobertgardzinski.security.domain.event.AuthenticationEvent;
import com.jrobertgardzinski.security.domain.event.BruteForceProtectionEvent;
import com.jrobertgardzinski.security.domain.vo.AuthenticationRequest;
import com.jrobertgardzinski.security.domain.vo.Credentials;
import com.jrobertgardzinski.security.domain.vo.Source;

public class Authentication {
    private final _BruteForceGuard bruteForceGuard;
    private final _VerifyCredentials verifyCredentials;
    private final _RequireVerifiedEmail requireVerifiedEmail;
    private final _GenerateSession generateSession;
    private final _CleanBruteForceRecords cleanBruteForceRecords;
    private final _UpdateBruteForceRecords updateBruteForceRecords;

    Authentication(_BruteForceGuard bruteForceGuard,
                   _VerifyCredentials verifyCredentials,
                   _RequireVerifiedEmail requireVerifiedEmail,
                   _GenerateSession generateSession,
                   _CleanBruteForceRecords cleanBruteForceRecords,
                   _UpdateBruteForceRecords updateBruteForceRecords) {
        this.bruteForceGuard = bruteForceGuard;
        this.verifyCredentials = verifyCredentials;
        this.requireVerifiedEmail = requireVerifiedEmail;
        this.generateSession = generateSession;
        this.cleanBruteForceRecords = cleanBruteForceRecords;
        this.updateBruteForceRecords = updateBruteForceRecords;
    }

    public AuthenticationResult execute(AuthenticationRequest request) {
        Source source = request.source();
        Credentials credentials = new Credentials(request.email(), request.plaintextPassword());

        return switch (bruteForceGuard.execute(source)) {
            case BruteForceProtectionEvent.Blocked blocked -> new AuthenticationResult.Blocked(blocked.authenticationBlock());
            case BruteForceProtectionEvent.Allowed _ -> switch (verifyCredentials.execute(credentials)) {
                case AuthenticationEvent.Valid valid -> {
                    // correct credentials are not a guessing signal, so no brute-force update here
                    if (!requireVerifiedEmail.isVerified(valid.email())) {
                        yield new AuthenticationResult.EmailNotVerified();
                    }
                    cleanBruteForceRecords.execute(source);
                    SessionTokens sessionTokens = generateSession.create(valid.email());
                    yield new AuthenticationResult.Authenticated(sessionTokens);
                }
                case AuthenticationEvent.Invalid _ -> {
                    updateBruteForceRecords.execute(source);
                    yield new AuthenticationResult.Rejected();
                }
            };
        };
    }
}

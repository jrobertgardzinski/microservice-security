package com.jrobertgardzinski.security.system.authentication;

import com.jrobertgardzinski.security.domain.entity.SessionTokens;
import com.jrobertgardzinski.security.domain.event.AuthenticationEvent;
import com.jrobertgardzinski.security.domain.event.BruteForceProtectionEvent;
import com.jrobertgardzinski.security.domain.vo.AuthenticationRequest;
import com.jrobertgardzinski.security.domain.vo.Credentials;
import com.jrobertgardzinski.security.domain.vo.IpAddress;

public class Authentication {
    private final _BruteForceGuard bruteForceGuard;
    private final _VerifyCredentials verifyCredentials;
    private final _GenerateSession generateSession;
    private final _CleanBruteForceRecords cleanBruteForceRecords;
    private final _UpdateBruteForceRecords updateBruteForceRecords;

    Authentication(_BruteForceGuard bruteForceGuard,
                   _VerifyCredentials verifyCredentials,
                   _GenerateSession generateSession,
                   _CleanBruteForceRecords cleanBruteForceRecords,
                   _UpdateBruteForceRecords updateBruteForceRecords) {
        this.bruteForceGuard = bruteForceGuard;
        this.verifyCredentials = verifyCredentials;
        this.generateSession = generateSession;
        this.cleanBruteForceRecords = cleanBruteForceRecords;
        this.updateBruteForceRecords = updateBruteForceRecords;
    }

    public AuthenticationResult execute(AuthenticationRequest request) {
        IpAddress ip = request.ipAddress();
        Credentials credentials = new Credentials(request.email(), request.plaintextPassword());

        return switch (bruteForceGuard.execute(ip)) {
            case BruteForceProtectionEvent.Blocked blocked -> new AuthenticationResult.Blocked(blocked.authenticationBlock());
            case BruteForceProtectionEvent.Passed _ -> switch (verifyCredentials.execute(credentials)) {
                case AuthenticationEvent.Passed passed -> {
                    cleanBruteForceRecords.execute(ip);
                    SessionTokens sessionTokens = generateSession.create(passed.email());
                    yield new AuthenticationResult.Passed(sessionTokens);
                }
                case AuthenticationEvent.Failed _ -> {
                    updateBruteForceRecords.execute(ip);
                    yield new AuthenticationResult.Failed();
                }
            };
        };
    }
}

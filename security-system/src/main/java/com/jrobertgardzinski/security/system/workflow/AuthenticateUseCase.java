package com.jrobertgardzinski.security.system.workflow;

import com.jrobertgardzinski.security.domain.entity.SessionTokens;
import com.jrobertgardzinski.security.domain.event.AuthenticationEvent;
import com.jrobertgardzinski.security.domain.event.BruteForceProtectionEvent;
import com.jrobertgardzinski.security.domain.vo.AuthenticationRequest;
import com.jrobertgardzinski.security.domain.vo.Credentials;
import com.jrobertgardzinski.security.domain.vo.IpAddress;
import com.jrobertgardzinski.security.system.event.AuthenticationResult;
import com.jrobertgardzinski.security.system.usecase.*;

public class AuthenticateUseCase {
    private final BruteForceGuard bruteForceGuard;
    private final VerifyCredentials verifyCredentials;
    private final GenerateSession generateSession;
    private final CleanBruteForceRecords cleanBruteForceRecords;
    private final UpdateBruteForceRecords updateBruteForceRecords;

    public AuthenticateUseCase(BruteForceGuard bruteForceGuard,
                               VerifyCredentials verifyCredentials,
                               GenerateSession generateSession,
                               CleanBruteForceRecords cleanBruteForceRecords,
                               UpdateBruteForceRecords updateBruteForceRecords) {
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

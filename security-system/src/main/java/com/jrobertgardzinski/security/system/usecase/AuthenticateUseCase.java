package com.jrobertgardzinski.security.system.usecase;

import com.jrobertgardzinski.security.domain.entity.SessionTokens;
import com.jrobertgardzinski.security.domain.event.authentication.AuthenticationFailedEvent;
import com.jrobertgardzinski.security.domain.event.authentication.AuthenticationPassedEvent;
import com.jrobertgardzinski.security.domain.event.brute.force.protection.Blocked;
import com.jrobertgardzinski.security.domain.event.brute.force.protection.Passed;
import com.jrobertgardzinski.security.domain.vo.AuthenticationRequest;
import com.jrobertgardzinski.security.domain.vo.Credentials;
import com.jrobertgardzinski.security.domain.vo.IpAddress;
import com.jrobertgardzinski.security.system.event.AuthenticationResult;
import com.jrobertgardzinski.security.system.feature.*;

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
            case Blocked blocked -> new AuthenticationResult.Blocked(blocked.authenticationBlock());
            case Passed _ -> switch (verifyCredentials.execute(credentials)) {
                case AuthenticationPassedEvent passed -> {
                    cleanBruteForceRecords.execute(ip);
                    SessionTokens sessionTokens = generateSession.create(passed.email());
                    yield new AuthenticationResult.Passed(sessionTokens);
                }
                case AuthenticationFailedEvent _ -> {
                    updateBruteForceRecords.execute(ip);
                    yield new AuthenticationResult.Failed();
                }
            };
        };
    }
}

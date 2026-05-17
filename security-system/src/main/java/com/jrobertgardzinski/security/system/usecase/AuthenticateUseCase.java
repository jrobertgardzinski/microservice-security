package com.jrobertgardzinski.security.system.usecase;

import com.jrobertgardzinski.security.domain.vo.AuthenticationRequest;
import com.jrobertgardzinski.security.domain.vo.Credentials;
import com.jrobertgardzinski.security.domain.vo.IpAddress;
import com.jrobertgardzinski.security.system.event.AuthenticationResult;
import com.jrobertgardzinski.security.system.feature.BruteForceGuard;
import com.jrobertgardzinski.security.system.feature.CleanBruteForceRecords;
import com.jrobertgardzinski.security.system.feature.GenerateSession;
import com.jrobertgardzinski.security.system.feature.UpdateBruteForceRecords;
import com.jrobertgardzinski.security.system.feature.VerifyCredentials;

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

        return AuthenticationResult.from(
                bruteForceGuard.execute(ip),
                () -> verifyCredentials.execute(credentials),
                passed -> {
                    cleanBruteForceRecords.execute(ip);
                    return generateSession.create(passed.email());
                },
                () -> updateBruteForceRecords.execute(ip));
    }
}

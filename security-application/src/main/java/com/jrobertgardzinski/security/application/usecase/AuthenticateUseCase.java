package com.jrobertgardzinski.security.application.usecase;

import com.jrobertgardzinski.security.system.event.AuthenticationBlocked;
import com.jrobertgardzinski.security.system.event.AuthenticationFailed;
import com.jrobertgardzinski.security.system.event.AuthenticationPassed;
import com.jrobertgardzinski.security.system.event.AuthenticationResult;
import com.jrobertgardzinski.security.system.feature.BruteForceGuard;
import com.jrobertgardzinski.security.system.feature.CleanBruteForceRecords;
import com.jrobertgardzinski.security.system.feature.GenerateSession;
import com.jrobertgardzinski.security.system.feature.UpdateBruteForceRecords;
import com.jrobertgardzinski.security.system.feature.VerifyCredentials;
import com.jrobertgardzinski.security.domain.entity.SessionTokens;
import com.jrobertgardzinski.security.domain.event.authentication.AuthenticationEvent;
import com.jrobertgardzinski.security.domain.event.authentication.AuthenticationFailedEvent;
import com.jrobertgardzinski.security.domain.event.authentication.AuthenticationPassedEvent;
import com.jrobertgardzinski.security.domain.event.brute.force.protection.Blocked;
import com.jrobertgardzinski.security.domain.event.brute.force.protection.BruteForceProtectionEvent;
import com.jrobertgardzinski.security.domain.event.brute.force.protection.Passed;
import com.jrobertgardzinski.security.domain.vo.*;

import java.util.function.BiFunction;

public class AuthenticateUseCase implements BiFunction<IpAddress, Credentials, AuthenticationResult> {
    private final VerifyCredentials verifyCredentials;
    private final BruteForceGuard bruteForceGuard;
    private final GenerateSession generateSession;
    private final CleanBruteForceRecords cleanBruteForceRecords;
    private final UpdateBruteForceRecords updateBruteForceRecords;

    public AuthenticateUseCase(VerifyCredentials verifyCredentials, BruteForceGuard bruteForceGuard, GenerateSession generateSession, CleanBruteForceRecords cleanBruteForceRecords, UpdateBruteForceRecords updateBruteForceRecords) {
        this.verifyCredentials = verifyCredentials;
        this.bruteForceGuard = bruteForceGuard;
        this.generateSession = generateSession;
        this.cleanBruteForceRecords = cleanBruteForceRecords;
        this.updateBruteForceRecords = updateBruteForceRecords;
    }

    @Override
    public AuthenticationResult apply(IpAddress ip, Credentials credentials) {
        BruteForceProtectionEvent bruteForceProtectionEvent = bruteForceGuard.apply(ip);
        switch (bruteForceProtectionEvent) {
            case Passed passed -> {
                AuthenticationEvent authenticationEvent = verifyCredentials.apply(credentials);
                switch (authenticationEvent) {
                    case AuthenticationPassedEvent authenticationPassedEvent -> {
                        cleanBruteForceRecords.accept(ip);
                        SessionTokens sessionTokens = generateSession.apply(authenticationPassedEvent);
                        return new AuthenticationPassed(sessionTokens);
                    }
                    case AuthenticationFailedEvent authenticationFailedEvent -> {
                        updateBruteForceRecords.accept(ip);
                        return new AuthenticationFailed();
                    }
                }
            }
            case Blocked blocked -> {
                return new AuthenticationBlocked(blocked.authenticationBlock());
            }
        }
    }
}

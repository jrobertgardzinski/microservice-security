package com.jrobertgardzinski.security.system.usecase;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.config.mfa.MfaConfig;
import com.jrobertgardzinski.security.domain.entity.AuthSession;
import com.jrobertgardzinski.security.domain.entity.SessionTokens;
import com.jrobertgardzinski.security.domain.event.authentication.AuthenticationEvent;
import com.jrobertgardzinski.security.domain.event.authentication.AuthenticationFailedEvent;
import com.jrobertgardzinski.security.domain.event.authentication.AuthenticationPassedEvent;
import com.jrobertgardzinski.security.domain.event.brute.force.protection.Blocked;
import com.jrobertgardzinski.security.domain.event.brute.force.protection.BruteForceProtectionEvent;
import com.jrobertgardzinski.security.domain.event.brute.force.protection.Passed;
import com.jrobertgardzinski.security.domain.repository.AuthSessionRepository;
import com.jrobertgardzinski.security.domain.repository.MfaPolicyPort;
import com.jrobertgardzinski.security.domain.vo.*;
import com.jrobertgardzinski.security.system.event.AuthenticationBlocked;
import com.jrobertgardzinski.security.system.event.AuthenticationFailed;
import com.jrobertgardzinski.security.system.event.AuthenticationPassed;
import com.jrobertgardzinski.security.system.event.AuthenticationPending;
import com.jrobertgardzinski.security.system.event.AuthenticationResult;
import com.jrobertgardzinski.security.system.feature.BruteForceGuard;
import com.jrobertgardzinski.security.system.feature.CleanBruteForceRecords;
import com.jrobertgardzinski.security.system.feature.GenerateSession;
import com.jrobertgardzinski.security.system.feature.IssueEmailOtpChallenge;
import com.jrobertgardzinski.security.system.feature.UpdateBruteForceRecords;
import com.jrobertgardzinski.security.system.feature.VerifyCredentials;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Function;

public class AuthenticateUseCase implements Function<AuthenticationRequest, AuthenticationResult> {
    private final VerifyCredentials verifyCredentials;
    private final BruteForceGuard bruteForceGuard;
    private final GenerateSession generateSession;
    private final CleanBruteForceRecords cleanBruteForceRecords;
    private final UpdateBruteForceRecords updateBruteForceRecords;
    private final MfaPolicyPort mfaPolicy;
    private final AuthSessionRepository authSessionRepository;
    private final IssueEmailOtpChallenge issueEmailOtpChallenge;
    private final Clock clock;
    private final MfaConfig mfaConfig;

    public AuthenticateUseCase(VerifyCredentials verifyCredentials, BruteForceGuard bruteForceGuard,
                               GenerateSession generateSession, CleanBruteForceRecords cleanBruteForceRecords,
                               UpdateBruteForceRecords updateBruteForceRecords, MfaPolicyPort mfaPolicy,
                               AuthSessionRepository authSessionRepository,
                               IssueEmailOtpChallenge issueEmailOtpChallenge,
                               Clock clock, MfaConfig mfaConfig) {
        this.verifyCredentials = verifyCredentials;
        this.bruteForceGuard = bruteForceGuard;
        this.generateSession = generateSession;
        this.cleanBruteForceRecords = cleanBruteForceRecords;
        this.updateBruteForceRecords = updateBruteForceRecords;
        this.mfaPolicy = mfaPolicy;
        this.authSessionRepository = authSessionRepository;
        this.issueEmailOtpChallenge = issueEmailOtpChallenge;
        this.clock = clock;
        this.mfaConfig = mfaConfig;
    }

    @Override
    public AuthenticationResult apply(AuthenticationRequest authenticationRequest) {
        IpAddress ip = authenticationRequest.ipAddress();
        BruteForceProtectionEvent bruteForceProtectionEvent = bruteForceGuard.apply(ip);
        switch (bruteForceProtectionEvent) {
            case Passed _ -> {
                Credentials credentials = new Credentials(
                        authenticationRequest.email(),
                        authenticationRequest.plaintextPassword()
                );
                AuthenticationEvent authenticationEvent = verifyCredentials.apply(credentials);
                switch (authenticationEvent) {
                    case AuthenticationPassedEvent passedEvent -> {
                        cleanBruteForceRecords.accept(ip);
                        return continueOrComplete(passedEvent);
                    }
                    case AuthenticationFailedEvent _ -> {
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

    private AuthenticationResult continueOrComplete(AuthenticationPassedEvent passedEvent) {
        Email email = passedEvent.email();
        List<FactorType> requiredFactors = mfaPolicy.requiredFactors(email);
        if (requiredFactors.size() <= 1) {
            SessionTokens tokens = generateSession.apply(passedEvent);
            return new AuthenticationPassed(tokens);
        }
        LocalDateTime expiresAt = LocalDateTime.now(clock).plusMinutes(mfaConfig.authSessionExpiryMinutes().value());
        AuthSession session = new AuthSession(AuthSessionId.generate(), email, requiredFactors, expiresAt);
        session.markFactorPassed(FactorType.CREDENTIALS);
        FactorType next = session.nextFactor().orElseThrow();
        if (next == FactorType.EMAIL_OTP) {
            session.setEmailOtpChallenge(issueEmailOtpChallenge.apply(email));
        }
        authSessionRepository.save(session);
        return new AuthenticationPending(session.id(), next, expiresAt);
    }
}

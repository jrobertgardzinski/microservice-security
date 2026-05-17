package com.jrobertgardzinski.security.system.usecase;

import com.jrobertgardzinski.security.domain.entity.AuthSession;
import com.jrobertgardzinski.security.domain.entity.SessionTokens;
import com.jrobertgardzinski.security.domain.event.authentication.AuthenticationPassedEvent;
import com.jrobertgardzinski.security.domain.repository.AuthSessionRepository;
import com.jrobertgardzinski.security.domain.repository.OtpCodeHasher;
import com.jrobertgardzinski.security.domain.vo.AuthSessionId;
import com.jrobertgardzinski.security.domain.vo.EmailOtpChallenge;
import com.jrobertgardzinski.security.domain.vo.EmailOtpVerificationRequest;
import com.jrobertgardzinski.security.domain.vo.FactorType;
import com.jrobertgardzinski.security.system.event.AuthenticationFailed;
import com.jrobertgardzinski.security.system.event.AuthenticationPassed;
import com.jrobertgardzinski.security.system.event.AuthenticationPending;
import com.jrobertgardzinski.security.system.event.AuthenticationResult;
import com.jrobertgardzinski.security.system.feature.GenerateSession;
import com.jrobertgardzinski.security.system.feature.IssueEmailOtpChallenge;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.function.Function;

public class VerifyEmailOtpUseCase implements Function<EmailOtpVerificationRequest, AuthenticationResult> {

    private final AuthSessionRepository authSessionRepository;
    private final OtpCodeHasher otpCodeHasher;
    private final GenerateSession generateSession;
    private final IssueEmailOtpChallenge issueEmailOtpChallenge;
    private final Clock clock;

    public VerifyEmailOtpUseCase(AuthSessionRepository authSessionRepository, OtpCodeHasher otpCodeHasher,
                                 GenerateSession generateSession,
                                 IssueEmailOtpChallenge issueEmailOtpChallenge, Clock clock) {
        this.authSessionRepository = authSessionRepository;
        this.otpCodeHasher = otpCodeHasher;
        this.generateSession = generateSession;
        this.issueEmailOtpChallenge = issueEmailOtpChallenge;
        this.clock = clock;
    }

    @Override
    public AuthenticationResult apply(EmailOtpVerificationRequest request) {
        AuthSessionId sessionId = request.authSessionId();
        Optional<AuthSession> maybeSession = authSessionRepository.findBy(sessionId);
        if (maybeSession.isEmpty()) {
            return new AuthenticationFailed();
        }
        AuthSession session = maybeSession.get();
        if (session.isExpired(clock)) {
            authSessionRepository.removeBy(sessionId);
            return new AuthenticationFailed();
        }
        if (session.nextFactor().orElse(null) != FactorType.EMAIL_OTP) {
            return new AuthenticationFailed();
        }
        Optional<EmailOtpChallenge> maybeChallenge = session.pendingEmailOtp();
        if (maybeChallenge.isEmpty() || maybeChallenge.get().isExpired(clock)) {
            return new AuthenticationFailed();
        }
        if (!otpCodeHasher.verify(maybeChallenge.get().codeHash(), request.code())) {
            return new AuthenticationFailed();
        }

        session.markFactorPassed(FactorType.EMAIL_OTP);

        if (session.isComplete()) {
            SessionTokens tokens = generateSession.apply(new AuthenticationPassedEvent(session.userEmail()));
            authSessionRepository.removeBy(sessionId);
            return new AuthenticationPassed(tokens);
        }

        FactorType next = session.nextFactor().orElseThrow();
        if (next == FactorType.EMAIL_OTP) {
            session.setEmailOtpChallenge(issueEmailOtpChallenge.apply(session.userEmail()));
        }
        authSessionRepository.save(session);
        LocalDateTime expiresAt = session.expiresAt();
        return new AuthenticationPending(session.id(), next, expiresAt);
    }
}

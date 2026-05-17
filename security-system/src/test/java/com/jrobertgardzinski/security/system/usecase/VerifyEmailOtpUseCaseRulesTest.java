package com.jrobertgardzinski.security.system.usecase;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.config.mfa.MfaConfig;
import com.jrobertgardzinski.security.domain.entity.AuthSession;
import com.jrobertgardzinski.security.domain.entity.SessionTokens;
import com.jrobertgardzinski.security.domain.vo.AuthSessionId;
import com.jrobertgardzinski.security.domain.vo.EmailOtpVerificationRequest;
import com.jrobertgardzinski.security.domain.vo.FactorType;
import com.jrobertgardzinski.security.domain.vo.OtpCode;
import com.jrobertgardzinski.security.system.event.AuthenticationFailed;
import com.jrobertgardzinski.security.system.event.AuthenticationPassed;
import com.jrobertgardzinski.security.system.event.AuthenticationResult;
import com.jrobertgardzinski.security.system.feature.CapturingEmailOtpSender;
import com.jrobertgardzinski.security.system.feature.GenerateSession;
import com.jrobertgardzinski.security.system.feature.InMemoryAuthSessionRepository;
import com.jrobertgardzinski.security.system.feature.IssueEmailOtpChallenge;
import com.jrobertgardzinski.security.system.feature.StubOtpCodeGenerator;
import com.jrobertgardzinski.security.system.feature.StubOtpCodeHasher;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VerifyEmailOtpUseCaseRulesTest {

    private static final Instant NOW = Instant.parse("2026-05-17T10:00:00Z");
    private static final Email USER_EMAIL = Email.of("user@example.com");
    private static final String VALID_CODE = "123456";
    private static final String WRONG_CODE = "999999";

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final MfaConfig mfaConfig = MfaConfig.builder().build();
    private final InMemoryAuthSessionRepository repo = new InMemoryAuthSessionRepository();
    private final StubOtpCodeHasher hasher = new StubOtpCodeHasher();
    private final StubOtpCodeGenerator generator = new StubOtpCodeGenerator(VALID_CODE);
    private final CapturingEmailOtpSender sender = new CapturingEmailOtpSender();
    private final IssueEmailOtpChallenge issueChallenge =
            new IssueEmailOtpChallenge(generator, hasher, sender, clock, mfaConfig);
    private final GenerateSession generateSession = mock(GenerateSession.class);

    private final VerifyEmailOtpUseCase useCase =
            new VerifyEmailOtpUseCase(repo, hasher, generateSession, issueChallenge, clock);

    @Test
    void correct_otp_on_final_factor_completes_with_session_tokens() {
        var session = seededPendingEmailOtpSession();
        var tokens = mock(SessionTokens.class);
        when(generateSession.apply(any())).thenReturn(tokens);

        AuthenticationResult result = useCase.apply(
                new EmailOtpVerificationRequest(session.id(), new OtpCode(VALID_CODE)));

        assertThat(result).isInstanceOfSatisfying(AuthenticationPassed.class,
                passed -> assertThat(passed.session()).isSameAs(tokens));
        assertThat(repo.size()).as("session removed after completion").isZero();
    }

    @Test
    void wrong_otp_fails_and_keeps_session_alive_for_retry() {
        var session = seededPendingEmailOtpSession();

        AuthenticationResult result = useCase.apply(
                new EmailOtpVerificationRequest(session.id(), new OtpCode(WRONG_CODE)));

        assertThat(result).isInstanceOf(AuthenticationFailed.class);
        assertThat(repo.findBy(session.id())).as("session preserved so user can retry").isPresent();
        verify(generateSession, never()).apply(any());
    }

    @Test
    void expired_session_fails_and_is_purged() {
        var expiredAt = LocalDateTime.now(clock).minusMinutes(1);
        var session = new AuthSession(AuthSessionId.generate(), USER_EMAIL,
                List.of(FactorType.CREDENTIALS, FactorType.EMAIL_OTP), expiredAt);
        session.markFactorPassed(FactorType.CREDENTIALS);
        session.setEmailOtpChallenge(issueChallenge.apply(USER_EMAIL));
        repo.save(session);

        AuthenticationResult result = useCase.apply(
                new EmailOtpVerificationRequest(session.id(), new OtpCode(VALID_CODE)));

        assertThat(result).isInstanceOf(AuthenticationFailed.class);
        assertThat(repo.findBy(session.id())).as("expired session pruned").isEmpty();
    }

    @Test
    void unknown_session_id_fails() {
        AuthenticationResult result = useCase.apply(
                new EmailOtpVerificationRequest(new AuthSessionId(UUID.randomUUID()), new OtpCode(VALID_CODE)));

        assertThat(result).isInstanceOf(AuthenticationFailed.class);
    }

    @Test
    void expired_challenge_fails_without_consuming_session_state() {
        var sessionExpiresAt = LocalDateTime.now(clock).plusMinutes(10);
        var session = new AuthSession(AuthSessionId.generate(), USER_EMAIL,
                List.of(FactorType.CREDENTIALS, FactorType.EMAIL_OTP), sessionExpiresAt);
        session.markFactorPassed(FactorType.CREDENTIALS);
        Clock past = Clock.fixed(NOW.minusSeconds(3600), ZoneOffset.UTC);
        var challengeFromPast = new IssueEmailOtpChallenge(
                new StubOtpCodeGenerator(VALID_CODE), hasher, sender, past, mfaConfig).apply(USER_EMAIL);
        session.setEmailOtpChallenge(challengeFromPast);
        repo.save(session);

        AuthenticationResult result = useCase.apply(
                new EmailOtpVerificationRequest(session.id(), new OtpCode(VALID_CODE)));

        assertThat(result).isInstanceOf(AuthenticationFailed.class);
        assertThat(repo.findBy(session.id())).isPresent();
    }

    private AuthSession seededPendingEmailOtpSession() {
        var expiresAt = LocalDateTime.now(clock).plusMinutes(mfaConfig.authSessionExpiryMinutes().value());
        var session = new AuthSession(AuthSessionId.generate(), USER_EMAIL,
                List.of(FactorType.CREDENTIALS, FactorType.EMAIL_OTP), expiresAt);
        session.markFactorPassed(FactorType.CREDENTIALS);
        session.setEmailOtpChallenge(issueChallenge.apply(USER_EMAIL));
        return repo.save(session);
    }
}

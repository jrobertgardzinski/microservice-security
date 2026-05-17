package com.jrobertgardzinski.security.system.usecase;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.password.domain.PlaintextPassword;
import com.jrobertgardzinski.security.config.mfa.MfaConfig;
import com.jrobertgardzinski.security.domain.entity.AuthSession;
import com.jrobertgardzinski.security.domain.entity.SessionTokens;
import com.jrobertgardzinski.security.domain.event.authentication.AuthenticationPassedEvent;
import com.jrobertgardzinski.security.domain.event.brute.force.protection.Blocked;
import com.jrobertgardzinski.security.domain.event.brute.force.protection.Passed;
import com.jrobertgardzinski.security.domain.entity.AuthenticationBlock;
import com.jrobertgardzinski.security.domain.vo.AuthenticationRequest;
import com.jrobertgardzinski.security.domain.vo.FactorType;
import com.jrobertgardzinski.security.domain.vo.IpAddress;
import com.jrobertgardzinski.security.system.event.AuthenticationBlocked;
import com.jrobertgardzinski.security.system.event.AuthenticationPassed;
import com.jrobertgardzinski.security.system.event.AuthenticationPending;
import com.jrobertgardzinski.security.system.event.AuthenticationResult;
import com.jrobertgardzinski.security.system.feature.BruteForceGuard;
import com.jrobertgardzinski.security.system.feature.CapturingEmailOtpSender;
import com.jrobertgardzinski.security.system.feature.CleanBruteForceRecords;
import com.jrobertgardzinski.security.system.feature.GenerateSession;
import com.jrobertgardzinski.security.system.feature.InMemoryAuthSessionRepository;
import com.jrobertgardzinski.security.system.feature.IssueEmailOtpChallenge;
import com.jrobertgardzinski.security.system.feature.StubMfaPolicy;
import com.jrobertgardzinski.security.system.feature.StubOtpCodeGenerator;
import com.jrobertgardzinski.security.system.feature.StubOtpCodeHasher;
import com.jrobertgardzinski.security.system.feature.UpdateBruteForceRecords;
import com.jrobertgardzinski.security.system.feature.VerifyCredentials;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthenticateUseCaseMfaRulesTest {

    private static final Instant NOW = Instant.parse("2026-05-17T10:00:00Z");
    private static final Email USER_EMAIL = Email.of("user@example.com");

    private final BruteForceGuard bruteForceGuard = mock(BruteForceGuard.class);
    private final VerifyCredentials verifyCredentials = mock(VerifyCredentials.class);
    private final GenerateSession generateSession = mock(GenerateSession.class);
    private final CleanBruteForceRecords cleanBruteForceRecords = mock(CleanBruteForceRecords.class);
    private final UpdateBruteForceRecords updateBruteForceRecords = mock(UpdateBruteForceRecords.class);

    private final InMemoryAuthSessionRepository authSessionRepository = new InMemoryAuthSessionRepository();
    private final StubOtpCodeGenerator otpGenerator = new StubOtpCodeGenerator("123456");
    private final StubOtpCodeHasher otpHasher = new StubOtpCodeHasher();
    private final CapturingEmailOtpSender otpSender = new CapturingEmailOtpSender();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final MfaConfig mfaConfig = MfaConfig.builder().build();
    private final IssueEmailOtpChallenge issueChallenge =
            new IssueEmailOtpChallenge(otpGenerator, otpHasher, otpSender, clock, mfaConfig);

    private final AuthenticationRequest request = new AuthenticationRequest(
            new IpAddress("127.0.0.1"), USER_EMAIL, PlaintextPassword.of("anything"));

    @Test
    void single_factor_user_gets_session_tokens_and_no_mfa_state() {
        var policy = new StubMfaPolicy(FactorType.CREDENTIALS);
        var useCase = newUseCase(policy);
        var tokens = mock(SessionTokens.class);
        when(bruteForceGuard.apply(any())).thenReturn(new Passed());
        when(verifyCredentials.apply(any())).thenReturn(new AuthenticationPassedEvent(USER_EMAIL));
        when(generateSession.apply(any())).thenReturn(tokens);

        AuthenticationResult result = useCase.apply(request);

        assertThat(result).isInstanceOfSatisfying(AuthenticationPassed.class,
                passed -> assertThat(passed.session()).isSameAs(tokens));
        verify(cleanBruteForceRecords).accept(request.ipAddress());
        assertThat(authSessionRepository.size()).isZero();
        assertThat(otpSender.count()).isZero();
    }

    @Test
    void two_factor_user_gets_pending_result_with_otp_emailed() {
        var policy = new StubMfaPolicy(FactorType.CREDENTIALS, FactorType.EMAIL_OTP);
        var useCase = newUseCase(policy);
        when(bruteForceGuard.apply(any())).thenReturn(new Passed());
        when(verifyCredentials.apply(any())).thenReturn(new AuthenticationPassedEvent(USER_EMAIL));

        AuthenticationResult result = useCase.apply(request);

        var pending = assertThat(result).isInstanceOf(AuthenticationPending.class)
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.type(AuthenticationPending.class))
                .actual();
        assertThat(pending.nextFactor()).isEqualTo(FactorType.EMAIL_OTP);
        assertThat(pending.expiresAt()).isAfter(LocalDateTime.now(clock));
        assertThat(authSessionRepository.size()).isOne();
        assertThat(authSessionRepository.findBy(pending.authSessionId())).hasValueSatisfying(session -> {
            assertThat(session.userEmail()).isEqualTo(USER_EMAIL);
            assertThat(session.factorsPassed()).containsExactly(FactorType.CREDENTIALS);
            assertThat(session.factorsRemaining()).containsExactly(FactorType.EMAIL_OTP);
            assertThat(session.pendingEmailOtp()).isPresent();
        });
        assertThat(otpSender.count()).isOne();
        assertThat(otpSender.last().recipient()).isEqualTo(USER_EMAIL);
        assertThat(otpSender.last().code().value()).isEqualTo("123456");
        verify(generateSession, never()).apply(any());
    }

    @Test
    void brute_force_block_short_circuits_before_credentials() {
        var policy = new StubMfaPolicy(FactorType.CREDENTIALS, FactorType.EMAIL_OTP);
        var useCase = newUseCase(policy);
        var block = new AuthenticationBlock(request.ipAddress(), LocalDateTime.now(clock).plusMinutes(5));
        when(bruteForceGuard.apply(any())).thenReturn(new Blocked(block));

        AuthenticationResult result = useCase.apply(request);

        assertThat(result).isInstanceOf(AuthenticationBlocked.class);
        verify(verifyCredentials, never()).apply(any());
        verify(generateSession, never()).apply(any());
        assertThat(authSessionRepository.size()).isZero();
        assertThat(otpSender.count()).isZero();
    }

    private AuthenticateUseCase newUseCase(StubMfaPolicy policy) {
        return new AuthenticateUseCase(verifyCredentials, bruteForceGuard, generateSession,
                cleanBruteForceRecords, updateBruteForceRecords, policy, authSessionRepository,
                issueChallenge, clock, mfaConfig);
    }
}

package com.jrobertgardzinski.security.system.feature.usecase;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.password.domain.HashedPassword;
import com.jrobertgardzinski.password.domain.PlaintextPassword;
import com.jrobertgardzinski.security.config.bruteforce.BruteForceConfig;
import com.jrobertgardzinski.security.domain.vo.AccessTokenValidityInHours;
import com.jrobertgardzinski.security.domain.vo.RefreshTokenValidityInHours;
import com.jrobertgardzinski.security.domain.vo.SessionTokensConfig;
import com.jrobertgardzinski.security.domain.entity.AuthenticationBlock;
import com.jrobertgardzinski.security.domain.entity.User;
import com.jrobertgardzinski.security.domain.vo.AuthenticationRequest;
import com.jrobertgardzinski.security.domain.vo.FailedAuthenticationDetails;
import com.jrobertgardzinski.security.domain.vo.IpAddress;
import com.jrobertgardzinski.security.domain.vo.token.TokenValidityInHours;
import com.jrobertgardzinski.security.system.event.AuthenticationBlocked;
import com.jrobertgardzinski.security.system.event.AuthenticationFailed;
import com.jrobertgardzinski.security.system.event.AuthenticationPassed;
import com.jrobertgardzinski.security.system.event.AuthenticationResult;
import com.jrobertgardzinski.security.system.feature.BruteForceGuard;
import com.jrobertgardzinski.security.system.feature.CleanBruteForceRecords;
import com.jrobertgardzinski.security.system.feature.GenerateSession;
import com.jrobertgardzinski.security.system.feature.UpdateBruteForceRecords;
import com.jrobertgardzinski.security.system.feature.VerifyCredentials;
import com.jrobertgardzinski.security.system.stub.StubAuthenticationBlockRepository;
import com.jrobertgardzinski.security.system.stub.StubAuthorizationDataRepository;
import com.jrobertgardzinski.security.system.stub.StubFailedAuthenticationRepository;
import com.jrobertgardzinski.security.system.stub.StubHashAlgorithm;
import com.jrobertgardzinski.security.system.stub.StubUserRepository;
import com.jrobertgardzinski.security.system.usecase.AuthenticateUseCase;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.time.Clock;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

public class AuthenticateRules {

    private final AuthenticateUseCase authenticateUseCase;
    private final StubUserRepository userRepository;
    private final StubHashAlgorithm hashAlgorithm;
    private final StubFailedAuthenticationRepository failedAuthenticationRepository;
    private final StubAuthenticationBlockRepository authenticationBlockRepository;

    private IpAddress ipAddress;
    private AuthenticationResult result;

    public AuthenticateRules(StubUserRepository userRepository,
                             StubHashAlgorithm hashAlgorithm,
                             StubFailedAuthenticationRepository failedAuthenticationRepository,
                             StubAuthenticationBlockRepository authenticationBlockRepository,
                             StubAuthorizationDataRepository authorizationDataRepository) {
        this.userRepository = userRepository;
        this.hashAlgorithm = hashAlgorithm;
        this.failedAuthenticationRepository = failedAuthenticationRepository;
        this.authenticationBlockRepository = authenticationBlockRepository;
        Clock clock = Clock.systemDefaultZone();
        VerifyCredentials verifyCredentials = new VerifyCredentials(userRepository, hashAlgorithm);
        BruteForceGuard bruteForceGuard = new BruteForceGuard(failedAuthenticationRepository, authenticationBlockRepository,
                clock, BruteForceConfig.builder().build());
        GenerateSession generateSession = new GenerateSession(authorizationDataRepository,
                clock, new SessionTokensConfig(new RefreshTokenValidityInHours(new TokenValidityInHours(24)), new AccessTokenValidityInHours(new TokenValidityInHours(1))));
        CleanBruteForceRecords cleanBruteForceRecords = new CleanBruteForceRecords(failedAuthenticationRepository, authenticationBlockRepository);
        UpdateBruteForceRecords updateBruteForceRecords = new UpdateBruteForceRecords(failedAuthenticationRepository, clock);
        this.authenticateUseCase = new AuthenticateUseCase(verifyCredentials, bruteForceGuard, generateSession, cleanBruteForceRecords, updateBruteForceRecords);
    }

    // background

    @Given("a registered user with email {string} and password {string}")
    public void givenRegisteredUser(String email, String password) {
        Email e = Email.of(email);
        PlaintextPassword p = PlaintextPassword.of(password);
        HashedPassword hashedPassword = hashAlgorithm.hash(p);
        try {
            userRepository.save(new User(e, hashedPassword));
        } catch (Exception ex) {
            fail("Failed to save user in background setup");
        }
    }

    @Given("the authentication attempt comes from IP {string}")
    public void givenAuthenticationAttemptFromIp(String ip) {
        ipAddress = new IpAddress(ip);
    }

    // given

    @Given("the IP has no blockade")
    public void givenNoBlockade() {
        // default state - no action needed
    }

    @Given("the IP has {int} recorded failures")
    public void givenRecordedFailures(int count) {
        for (int i = 0; i < count; i++) {
            failedAuthenticationRepository.create(
                    new FailedAuthenticationDetails(ipAddress, LocalDateTime.now()));
        }
    }

    @Given("the IP has {int} failures recorded {int} minutes ago")
    public void givenOldFailures(int count, int minutesAgo) {
        LocalDateTime time = LocalDateTime.now().minusMinutes(minutesAgo);
        for (int i = 0; i < count; i++) {
            failedAuthenticationRepository.create(
                    new FailedAuthenticationDetails(ipAddress, time));
        }
    }

    @Given("the IP has an active blockade")
    public void givenActiveBlockade() {
        authenticationBlockRepository.create(
                new AuthenticationBlock(ipAddress, LocalDateTime.now().plusMinutes(10)));
    }

    // when

    @When("the user authenticates with email {string} and password {string}")
    public void whenUserAuthenticates(String email, String password) {
        AuthenticationRequest request = new AuthenticationRequest(
                ipAddress,
                Email.of(email),
                PlaintextPassword.of(password)
        );
        result = authenticateUseCase.apply(request);
    }

    // then

    @Then("the authentication result is passed")
    public void thenAuthenticationPassed() {
        assertInstanceOf(AuthenticationPassed.class, result);
    }

    @Then("the authentication result is failed")
    public void thenAuthenticationFailed() {
        assertInstanceOf(AuthenticationFailed.class, result);
    }

    @Then("the authentication result is blocked")
    public void thenAuthenticationBlocked() {
        assertInstanceOf(AuthenticationBlocked.class, result);
    }
}

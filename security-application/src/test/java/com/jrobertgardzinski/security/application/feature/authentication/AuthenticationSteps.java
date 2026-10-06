package com.jrobertgardzinski.security.application.feature.authentication;

import com.jrobertgardzinski.security.domain.authentication.FakeAuthenticationBlockRepository;
import com.jrobertgardzinski.security.domain.mailbox.FakeEmailVerificationRepository;
import com.jrobertgardzinski.security.domain.authentication.FakeRejectedAuthenticationRepository;
import com.jrobertgardzinski.security.domain.session.FakeSessionRepository;
import com.jrobertgardzinski.security.domain.core.FakeUserRepository;
import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.password.domain.PlaintextPassword;
import com.jrobertgardzinski.security.application.feature.support.FakeHashAlgorithm;
import com.jrobertgardzinski.clock.AdjustableClock;
import com.jrobertgardzinski.security.config.authentication.BruteForceConfig;
import com.jrobertgardzinski.security.domain.authentication.AuthenticationBlock;
import com.jrobertgardzinski.security.domain.core.User;
import com.jrobertgardzinski.security.domain.session.AccessTokenValidityInHours;
import com.jrobertgardzinski.security.domain.authentication.AuthenticationRequest;
import com.jrobertgardzinski.security.domain.core.IpAddress;
import com.jrobertgardzinski.security.domain.core.Source;
import com.jrobertgardzinski.security.domain.session.RefreshTokenValidityInHours;
import com.jrobertgardzinski.security.domain.core.AttemptedAccount;
import com.jrobertgardzinski.security.domain.core.LockoutSubject;
import com.jrobertgardzinski.security.domain.authentication.RejectedAuthenticationDetails;
import com.jrobertgardzinski.security.domain.session.SessionTokensConfig;
import com.jrobertgardzinski.security.system.authentication.Authentication;
import com.jrobertgardzinski.security.system.authentication.AuthenticationFactory;
import com.jrobertgardzinski.security.system.authentication.AuthenticationResult;
import com.jrobertgardzinski.security.system.authentication.BlockDurationPolicy;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class AuthenticationSteps {

    private static final Source SOURCE = Source.of(new IpAddress("192.168.0.1"));
    private static final Email UNKNOWN_EMAIL = Email.of("other@example.com");
    private static final PlaintextPassword WRONG_PASSWORD = PlaintextPassword.of("WrongButStrongPassword1!");
    private static final int FIXED_BLOCK_MINUTES = 5;
    private static final SessionTokensConfig SESSION_TOKENS_CONFIG = new SessionTokensConfig(
            new RefreshTokenValidityInHours(24), new AccessTokenValidityInHours(1));
    private static final Pattern INTEGER = Pattern.compile("\\d+");

    private final FakeUserRepository users = new FakeUserRepository();
    private final FakeEmailVerificationRepository verifications = new FakeEmailVerificationRepository(java.time.Clock.systemUTC());
    private final FakeRejectedAuthenticationRepository rejections = new FakeRejectedAuthenticationRepository();
    private final FakeAuthenticationBlockRepository blocks = new FakeAuthenticationBlockRepository();
    private final AdjustableClock clock = new AdjustableClock(Instant.parse("2026-06-15T10:00:00Z"), ZoneOffset.UTC);
    // declared after the clock on purpose: the session store now answers listActiveSessions against
    // it, the same way both production adapters do
    private final FakeSessionRepository sessions = new FakeSessionRepository(clock);
    private final FakeHashAlgorithm hashAlgorithm = new FakeHashAlgorithm();
    private final BlockDurationPolicy blockDuration = () -> FIXED_BLOCK_MINUTES;

    private Email registeredEmail;
    private PlaintextPassword registeredPassword;
    private BruteForceConfig config;
    private Authentication authentication;
    private AuthenticationResult result;

    // --- Background -----------------------------------------------------------

    @Given("a registered USER {string} with password {string}")
    public void aRegisteredUser(String email, String password) {
        // "registered" implies a completed onboarding: the address has been verified
        aRegisteredButUnverifiedUser(email, password);
        verifications.markVerified(registeredEmail);
    }

    @Given("a registered USER {string} with password {string} whose EMAIL is not verified yet")
    public void aRegisteredButUnverifiedUser(String email, String password) {
        registeredEmail = Email.of(email);
        registeredPassword = PlaintextPassword.of(password);
        users.save(new User(registeredEmail, hashAlgorithm.hash(registeredPassword)));
    }

    @Given("AUTHENTICATION attempts from one source are limited by this policy:")
    public void thePolicy(DataTable policy) {
        BruteForceConfig.Builder builder = BruteForceConfig.builder();
        for (List<String> row : policy.asLists()) {
            String key = row.get(0);
            List<Integer> numbers = integersIn(row.get(1));
            if (key.contains("failed attempts")) {
                builder.maxFailures(numbers.get(0));
            } else if (key.contains("only count within")) {
                builder.failureWindowMinutes(numbers.get(0));
            } else if (key.contains("block lasts")) {
                builder.minBlockMinutes(numbers.get(0)).maxBlockMinutes(numbers.get(1));
            }
        }
        config = builder.build();
    }

    // --- Givens ---------------------------------------------------------------

    @Given("the USER has reached the failure limit")
    public void reachedFailureLimit() {
        recordFailures(config.maxFailures().value());
    }

    @Given("the USER has failed to AUTHENTICATE but stayed under the limit")
    public void underFailureLimit() {
        recordFailures(config.maxFailures().value() - 1);
    }

    @Given("the source is blocked")
    public void sourceIsBlocked() {
        blocks.create(new AuthenticationBlock(SOURCE, LocalDateTime.now(clock).plusMinutes(blockDuration.blockMinutes())));
    }

    // --- Whens ----------------------------------------------------------------

    @When("the USER AUTHENTICATES with the correct CREDENTIALS")
    public void authenticatesCorrectly() {
        result = authentication().execute(new AuthenticationRequest(SOURCE, registeredEmail, registeredPassword));
    }

    @When("{int} minutes passes")
    public void skip(int minutes) {
        clock.advance(Duration.ofMinutes(minutes));
    }

    @When("^the USER tries to AUTHENTICATE with (.+)$")
    public void triesToAuthenticateWith(String wrongCredentials) {
        Email email;
        PlaintextPassword password;
        switch (wrongCredentials) {
            case "the wrong password" -> {
                email = registeredEmail;
                password = WRONG_PASSWORD;
            }
            case "an unknown email" -> {
                email = UNKNOWN_EMAIL;
                password = registeredPassword;
            }
            case "a wrong email and password" -> {
                email = UNKNOWN_EMAIL;
                password = WRONG_PASSWORD;
            }
            default -> throw new IllegalArgumentException("Unknown credentials case: " + wrongCredentials);
        }
        result = authentication().execute(new AuthenticationRequest(SOURCE, email, password));
    }

    @When("the block expires")
    public void theBlockExpires() {
        clock.advance(Duration.ofMinutes(blockDuration.blockMinutes()));
    }

    // --- Thens ----------------------------------------------------------------

    @Then("the USER is AUTHENTICATED")
    public void userIsAuthenticated() {
        assertInstanceOf(AuthenticationResult.Authenticated.class, result);
    }

    @Then("the AUTHENTICATION is rejected")
    public void authenticationIsRejected() {
        assertInstanceOf(AuthenticationResult.Rejected.class, result);
    }

    @Then("the AUTHENTICATION is rejected because the source is blocked")
    public void authenticationIsBlocked() {
        assertInstanceOf(AuthenticationResult.Blocked.class, result);
    }

    @Then("the AUTHENTICATION is rejected because the EMAIL is not verified")
    public void authenticationIsRejectedAsUnverified() {
        assertInstanceOf(AuthenticationResult.EmailNotVerified.class, result);
    }

    // --- Helpers --------------------------------------------------------------

    private Authentication authentication() {
        if (authentication == null) {
            // these scenarios enrol no factors, so the chain is empty and sign-in is single-factor
            authentication = AuthenticationFactory.assemble(
                    users, verifications, rejections, blocks, hashAlgorithm, config, clock, blockDuration,
                    new com.jrobertgardzinski.security.system.session.SessionKeeper(sessions, clock, SESSION_TOKENS_CONFIG,
                            com.jrobertgardzinski.security.domain.session.AccessTokenMint.RANDOM),
                    new com.jrobertgardzinski.security.system.authentication.RecordFailedSignIn(rejections, clock),
                    new com.jrobertgardzinski.security.system.mfa.BeginFactorChain(
                            new com.jrobertgardzinski.security.domain.mfa.FakeEnrolledFactorRepository(),
                            new com.jrobertgardzinski.security.system.mfa.MfaChain(
                                    new com.jrobertgardzinski.security.system.mfa.FactorRegistry(java.util.List.of()),
                                    com.jrobertgardzinski.security.config.mfa.ChallengeCodeConfig.withDefaults(),
                                    new com.jrobertgardzinski.security.domain.mfa.FakeRecoveryCodeRepository(),
                                    raw -> "hash:" + raw, clock, 10),
                            new com.jrobertgardzinski.security.domain.mfa.FakePendingAuthenticationStore()));
        }
        return authentication;
    }

    /**
     * Seed failures against the PAIR the scenario then signs in as — the source AND the registered
     * account. Charging them to the source alone would seed a bucket nothing reads: since the
     * lockout stopped being keyed by address alone, the count these examples are about is the tight
     * per-victim one, and the source-wide ceiling above it is deliberately much higher.
     */
    private void recordFailures(int count) {
        LockoutSubject subject = new LockoutSubject(SOURCE, AttemptedAccount.of(registeredEmail));
        for (int i = 0; i < count; i++) {
            rejections.create(new RejectedAuthenticationDetails(subject, LocalDateTime.now(clock)));
        }
    }

    private static List<Integer> integersIn(String text) {
        Matcher matcher = INTEGER.matcher(text);
        List<Integer> numbers = new ArrayList<>();
        while (matcher.find()) {
            numbers.add(Integer.parseInt(matcher.group()));
        }
        return numbers;
    }
}

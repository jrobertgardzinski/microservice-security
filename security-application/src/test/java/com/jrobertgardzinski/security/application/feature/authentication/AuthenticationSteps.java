package com.jrobertgardzinski.security.application.feature.authentication;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.password.domain.PlaintextPassword;
import com.jrobertgardzinski.security.application.feature.support.FakeHashAlgorithm;
import com.jrobertgardzinski.security.application.feature.support.InMemoryAuthenticationBlockRepository;
import com.jrobertgardzinski.security.application.feature.support.InMemoryAuthorizationDataRepository;
import com.jrobertgardzinski.security.application.feature.support.InMemoryRejectedAuthenticationRepository;
import com.jrobertgardzinski.security.application.feature.support.InMemoryUserRepository;
import com.jrobertgardzinski.clock.AdjustableClock;
import com.jrobertgardzinski.security.config.bruteforce.BruteForceConfig;
import com.jrobertgardzinski.security.domain.entity.AuthenticationBlock;
import com.jrobertgardzinski.security.domain.entity.User;
import com.jrobertgardzinski.security.domain.vo.AccessTokenValidityInHours;
import com.jrobertgardzinski.security.domain.vo.AuthenticationRequest;
import com.jrobertgardzinski.security.domain.vo.IpAddress;
import com.jrobertgardzinski.security.domain.vo.RefreshTokenValidityInHours;
import com.jrobertgardzinski.security.domain.vo.RejectedAuthenticationDetails;
import com.jrobertgardzinski.security.domain.vo.SessionTokensConfig;
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

    private static final IpAddress SOURCE = new IpAddress("192.168.0.1");
    private static final Email UNKNOWN_EMAIL = Email.of("other@example.com");
    private static final PlaintextPassword WRONG_PASSWORD = PlaintextPassword.of("WrongButStrongPassword1!");
    private static final int FIXED_BLOCK_MINUTES = 5;
    private static final SessionTokensConfig SESSION_TOKENS_CONFIG = new SessionTokensConfig(
            new RefreshTokenValidityInHours(24), new AccessTokenValidityInHours(1));
    private static final Pattern INTEGER = Pattern.compile("\\d+");

    private final InMemoryUserRepository users = new InMemoryUserRepository();
    private final InMemoryRejectedAuthenticationRepository rejections = new InMemoryRejectedAuthenticationRepository();
    private final InMemoryAuthenticationBlockRepository blocks = new InMemoryAuthenticationBlockRepository();
    private final InMemoryAuthorizationDataRepository sessions = new InMemoryAuthorizationDataRepository();
    private final FakeHashAlgorithm hashAlgorithm = new FakeHashAlgorithm();
    private final AdjustableClock clock = new AdjustableClock(Instant.parse("2026-06-15T10:00:00Z"), ZoneOffset.UTC);
    private final BlockDurationPolicy blockDuration = () -> FIXED_BLOCK_MINUTES;

    private Email registeredEmail;
    private PlaintextPassword registeredPassword;
    private BruteForceConfig config;
    private Authentication authentication;
    private AuthenticationResult result;

    // --- Background -----------------------------------------------------------

    @Given("a registered user {string} with password {string}")
    public void aRegisteredUser(String email, String password) {
        registeredEmail = Email.of(email);
        registeredPassword = PlaintextPassword.of(password);
        users.save(new User(registeredEmail, hashAlgorithm.hash(registeredPassword)));
    }

    @Given("authentication attempts from one source are limited by this policy:")
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

    @Given("the user has reached the failure limit")
    public void reachedFailureLimit() {
        recordFailures(config.maxFailures().value());
    }

    @Given("the user has failed to authenticate but stayed under the limit")
    public void underFailureLimit() {
        recordFailures(config.maxFailures().value() - 1);
    }

    @Given("the source is blocked")
    public void sourceIsBlocked() {
        blocks.create(new AuthenticationBlock(SOURCE, LocalDateTime.now(clock).plusMinutes(blockDuration.blockMinutes())));
    }

    // --- Whens ----------------------------------------------------------------

    @When("the user authenticates with the correct credentials")
    public void authenticatesCorrectly() {
        result = authentication().execute(new AuthenticationRequest(SOURCE, registeredEmail, registeredPassword));
    }

    @When("{int} minutes passes")
    public void skip(int minutes) {
        clock.advance(Duration.ofMinutes(minutes));
    }

    @When("^the user tries to authenticate with (.+)$")
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

    @Then("the user is authenticated")
    public void userIsAuthenticated() {
        assertInstanceOf(AuthenticationResult.Authenticated.class, result);
    }

    @Then("the authentication is rejected")
    public void authenticationIsRejected() {
        assertInstanceOf(AuthenticationResult.Rejected.class, result);
    }

    @Then("the authentication is rejected because the source is blocked")
    public void authenticationIsBlocked() {
        assertInstanceOf(AuthenticationResult.Blocked.class, result);
    }

    // --- Helpers --------------------------------------------------------------

    private Authentication authentication() {
        if (authentication == null) {
            authentication = AuthenticationFactory.create(
                    users, rejections, blocks, sessions, hashAlgorithm,
                    config, SESSION_TOKENS_CONFIG, clock, blockDuration);
        }
        return authentication;
    }

    private void recordFailures(int count) {
        for (int i = 0; i < count; i++) {
            rejections.create(new RejectedAuthenticationDetails(SOURCE, LocalDateTime.now(clock)));
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

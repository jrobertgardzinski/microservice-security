package com.jrobertgardzinski.security.application.feature.session;

import com.jrobertgardzinski.security.domain.session.FakeSessionRepository;
import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.session.SessionTokens;
import com.jrobertgardzinski.security.domain.session.AccessTokenValidityInHours;
import com.jrobertgardzinski.security.domain.session.RefreshTokenValidityInHours;
import com.jrobertgardzinski.security.domain.session.SessionRefreshRequest;
import com.jrobertgardzinski.security.domain.session.SessionTokensConfig;
import com.jrobertgardzinski.security.domain.session.AccessToken;
import com.jrobertgardzinski.security.domain.session.RefreshToken;
import com.jrobertgardzinski.security.domain.session.AccessTokenExpiration;
import com.jrobertgardzinski.security.domain.session.RefreshTokenExpiration;
import com.jrobertgardzinski.security.system.session.RefreshSession;
import com.jrobertgardzinski.security.system.session.RefreshSessionResult;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class SessionSteps {

    private static final RefreshToken TOKEN = new RefreshToken("refresh-token");
    private static final SessionTokensConfig CONFIG = new SessionTokensConfig(
            new RefreshTokenValidityInHours(24), new AccessTokenValidityInHours(1));

    private final Clock clock = Clock.fixed(Instant.parse("2026-06-15T10:00:00Z"), ZoneOffset.UTC);
    private final FakeSessionRepository authorizationData = new FakeSessionRepository(clock);
    private final RefreshSession refreshSession = new RefreshSession(authorizationData, clock, CONFIG, com.jrobertgardzinski.security.domain.session.AccessTokenMint.RANDOM,
            java.time.Duration.ofDays(30));

    private Email email;
    private RefreshSessionResult result;

    @Given("a registered USER {string}")
    public void aRegisteredUser(String email) {
        this.email = Email.of(email);
    }

    @Given("the USER has an active session")
    public void theUserHasAnActiveSession() {
        authorizationData.store(sessionExpiringAt(LocalDateTime.now(clock).plusHours(1)));
    }

    @Given("the USER'S session has expired")
    public void theUsersSessionHasExpired() {
        authorizationData.store(sessionExpiringAt(LocalDateTime.now(clock).minusHours(1)));
    }

    @Given("the USER has no session")
    public void theUserHasNoSession() {
        // nothing stored — findByRefreshToken will return empty
    }

    @When("the USER REFRESHES the session")
    public void theUserRefreshesTheSession() {
        result = refreshSession.execute(new SessionRefreshRequest(TOKEN));
    }

    @Then("a fresh session is returned")
    public void aFreshSessionIsReturned() {
        assertInstanceOf(RefreshSessionResult.Refreshed.class, result);
    }

    @Then("the REFRESH is rejected because the session has expired")
    public void rejectedAsExpired() {
        assertInstanceOf(RefreshSessionResult.Expired.class, result);
    }

    @Then("the REFRESH is rejected because there is no session to REFRESH")
    public void rejectedAsNotFound() {
        assertInstanceOf(RefreshSessionResult.NotFound.class, result);
    }

    private SessionTokens sessionExpiringAt(LocalDateTime refreshExpiry) {
        return new SessionTokens(
                email,
                TOKEN,
                AccessToken.random(),
                new RefreshTokenExpiration(refreshExpiry),
                AccessTokenExpiration.validInHours(new AccessTokenValidityInHours(1), clock));
    }
}

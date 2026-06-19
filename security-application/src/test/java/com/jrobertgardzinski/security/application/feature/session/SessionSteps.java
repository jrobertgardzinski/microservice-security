package com.jrobertgardzinski.security.application.feature.session;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.application.feature.support.InMemoryAuthorizationDataRepository;
import com.jrobertgardzinski.security.domain.vo.AccessTokenValidityInHours;
import com.jrobertgardzinski.security.domain.vo.RefreshTokenValidityInHours;
import com.jrobertgardzinski.security.domain.vo.SessionRefreshRequest;
import com.jrobertgardzinski.security.domain.vo.SessionTokensConfig;
import com.jrobertgardzinski.security.domain.vo.token.RefreshToken;
import com.jrobertgardzinski.security.domain.vo.token.expiration.RefreshTokenExpiration;
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
    private final InMemoryAuthorizationDataRepository authorizationData = new InMemoryAuthorizationDataRepository();
    private final RefreshSession refreshSession = new RefreshSession(authorizationData, clock, CONFIG);

    private Email email;
    private RefreshSessionResult result;

    @Given("a registered user {string}")
    public void aRegisteredUser(String email) {
        this.email = Email.of(email);
    }

    @Given("the user has an active session")
    public void theUserHasAnActiveSession() {
        authorizationData.store(email, new RefreshTokenExpiration(LocalDateTime.now(clock).plusHours(1)));
    }

    @Given("the user's session has expired")
    public void theUsersSessionHasExpired() {
        authorizationData.store(email, new RefreshTokenExpiration(LocalDateTime.now(clock).minusHours(1)));
    }

    @Given("the user has no session")
    public void theUserHasNoSession() {
        // nothing stored — findRefreshTokenExpirationBy will return null
    }

    @When("the user refreshes the session")
    public void theUserRefreshesTheSession() {
        result = refreshSession.execute(new SessionRefreshRequest(email, TOKEN));
    }

    @Then("a fresh session is returned")
    public void aFreshSessionIsReturned() {
        assertInstanceOf(RefreshSessionResult.Refreshed.class, result);
    }

    @Then("the refresh is rejected because the session has expired")
    public void rejectedAsExpired() {
        assertInstanceOf(RefreshSessionResult.Expired.class, result);
    }

    @Then("the refresh is rejected because there is no session to refresh")
    public void rejectedAsNotFound() {
        assertInstanceOf(RefreshSessionResult.NotFound.class, result);
    }
}

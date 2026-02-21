package com.jrobertgardzinski.token.feature;

import com.jrobertgardzinski.token.domain.*;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

public class TokenRules {

    private Token first;
    private Token second;
    private IllegalArgumentException rejected;
    private RefreshTokenExpiration refreshTokenExpiration;

    @When("a random token is generated")
    public void aRandomTokenIsGenerated() {
        first = Token.random();
    }

    @When("two random tokens are generated")
    public void twoRandomTokensAreGenerated() {
        first = Token.random();
        second = Token.random();
    }

    @When("a token is created with a blank value")
    public void aTokenIsCreatedWithABlankValue() {
        try {
            first = new Token("  ");
            rejected = null;
        } catch (IllegalArgumentException e) {
            first = null;
            rejected = e;
        }
    }

    @Given("a refresh token that expired in the past")
    public void aRefreshTokenThatExpiredInThePast() {
        Clock past = Clock.fixed(Instant.parse("2020-01-01T00:00:00Z"), ZoneOffset.UTC);
        TokenExpiration expiration = TokenExpiration.validInHours(1, past);
        refreshTokenExpiration = new RefreshTokenExpiration(expiration);
    }

    @When("expiration is checked")
    public void expirationIsChecked() {
        // checking happens in the Then step using current clock
    }

    @Then("the token value is not blank")
    public void theTokenValueIsNotBlank() {
        assertFalse(first.value().isBlank());
    }

    @Then("the tokens are different")
    public void theTokensAreDifferent() {
        assertNotEquals(first, second);
    }

    @Then("the token creation should be rejected")
    public void theTokenCreationShouldBeRejected() {
        assertNotNull(rejected, "Expected token creation to be rejected but it was accepted");
    }

    @Then("it can be wrapped as an access token")
    public void itCanBeWrappedAsAnAccessToken() {
        AccessToken accessToken = new AccessToken(first);
        assertEquals(first, accessToken.value());
    }

    @Then("the token is expired")
    public void theTokenIsExpired() {
        Clock now = Clock.fixed(Instant.parse("2025-01-01T00:00:00Z"), ZoneOffset.UTC);
        assertTrue(refreshTokenExpiration.hasExpired(now));
    }
}

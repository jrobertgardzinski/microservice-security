package com.jrobertgardzinski.token.feature;

import com.jrobertgardzinski.token.config.SessionConfig;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class SessionConfigRules {

    private SessionConfig.Builder builder;
    private SessionConfig config;
    private IllegalArgumentException rejected;

    @Given("a new session configuration is initialized")
    public void aNewSessionConfigurationIsInitialized() {
        builder = SessionConfig.builder();
    }

    @When("no specific properties are provided")
    public void noSpecificPropertiesAreProvided() {
        config = builder.build();
    }

    @When("the refresh token validity is set to {int} hours")
    public void theRefreshTokenValidityIsSetToHours(int hours) {
        try {
            builder.refreshTokenValidityHours(hours);
            config = builder.build();
            rejected = null;
        } catch (IllegalArgumentException e) {
            config = null;
            rejected = e;
        }
    }

    @When("the access token validity is set to {int} hour")
    public void theAccessTokenValidityIsSetToHour(int hours) {
        builder.accessTokenValidityHours(hours);
        config = builder.build();
    }

    @Then("the session config should adhere to the default values:")
    public void theSessionConfigShouldAdhereToTheDefaultValues(Map<String, String> expected) {
        assertEquals(Integer.parseInt(expected.get("refreshTokenValidityHours")), config.refreshTokenValidityHours());
        assertEquals(Integer.parseInt(expected.get("accessTokenValidityHours")), config.accessTokenValidityHours());
    }

    @Then("the refresh token validity should be {int} hours")
    public void theRefreshTokenValidityShouldBeHours(int expected) {
        assertEquals(expected, config.refreshTokenValidityHours());
    }

    @Then("the access token validity should be {int} hour")
    public void theAccessTokenValidityShouldBeHour(int expected) {
        assertEquals(expected, config.accessTokenValidityHours());
    }

    @Then("the session configuration should be rejected")
    public void theSessionConfigurationShouldBeRejected() {
        assertNotNull(rejected, "Expected configuration to be rejected but it was accepted");
    }
}

package com.jrobertgardzinski.security.config.feature;

import com.jrobertgardzinski.security.domain.config.SessionConfig;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SessionConfigRules {

    private SessionConfig config;

    @Given("a new session configuration is initialized")
    public void aNewSessionConfigurationIsInitialized() {
        config = SessionConfig.builder().build();
    }

    @Then("the session config should adhere to the default security rules:")
    public void theSessionConfigShouldAdhereToTheDefaultSecurityRules(Map<String, String> expectedRules) {
        assertEquals(Integer.parseInt(expectedRules.get("refreshTokenValidityHours")), config.refreshTokenValidityHours());
        assertEquals(Integer.parseInt(expectedRules.get("accessTokenValidityHours")), config.accessTokenValidityHours());
    }
}

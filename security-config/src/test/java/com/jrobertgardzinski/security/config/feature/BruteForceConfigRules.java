package com.jrobertgardzinski.security.config.feature;

import com.jrobertgardzinski.security.domain.config.BruteForceConfig;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BruteForceConfigRules {

    private BruteForceConfig config;

    @Given("a new brute force configuration is initialized")
    public void aNewBruteForceConfigurationIsInitialized() {
        config = BruteForceConfig.builder().build();
    }

    @Then("the brute force config should adhere to the default security rules:")
    public void theBruteForceConfigShouldAdhereToTheDefaultSecurityRules(Map<String, String> expectedRules) {
        assertEquals(Integer.parseInt(expectedRules.get("failureWindowMinutes")), config.failureWindowMinutes());
        assertEquals(Integer.parseInt(expectedRules.get("maxFailures")), config.maxFailures());
        assertEquals(Integer.parseInt(expectedRules.get("minBlockMinutes")), config.minBlockMinutes());
        assertEquals(Integer.parseInt(expectedRules.get("maxBlockMinutes")), config.maxBlockMinutes());
    }
}

package com.jrobertgardzinski.security.config.feature;

import com.jrobertgardzinski.security.domain.config.SaltConfig;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SaltConfigRules {

    private SaltConfig config;

    @Given("a new salt configuration is initialized")
    public void aNewSaltConfigurationIsInitialized() {
        config = SaltConfig.builder().build();
    }

    @Then("the salt config should adhere to the default security rules:")
    public void theSaltConfigShouldAdhereToTheDefaultSecurityRules(Map<String, String> expectedRules) {
        assertEquals(Integer.parseInt(expectedRules.get("byteLength")), config.byteLength());
    }
}

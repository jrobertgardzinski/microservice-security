package com.jrobertgardzinski.salt.feature;

import com.jrobertgardzinski.salt.config.SaltConfig;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class SaltConfigRules {

    private SaltConfig.Builder builder;
    private SaltConfig config;
    private IllegalArgumentException rejected;

    @Given("a new salt configuration is initialized")
    public void aNewSaltConfigurationIsInitialized() {
        builder = SaltConfig.builder();
    }

    @When("no specific properties are provided")
    public void noSpecificPropertiesAreProvided() {
        config = builder.build();
    }

    @When("the byte length is set to {int}")
    public void theByteLengthIsSetTo(int byteLength) {
        try {
            config = builder.byteLength(byteLength).build();
            rejected = null;
        } catch (IllegalArgumentException e) {
            config = null;
            rejected = e;
        }
    }

    @Then("the salt config should adhere to the default security rules:")
    public void theSaltConfigShouldAdhereToTheDefaultSecurityRules(Map<String, String> expected) {
        assertEquals(Integer.parseInt(expected.get("byteLength")), config.byteLength());
    }

    @Then("the salt byte length should be {int}")
    public void theSaltByteLengthShouldBe(int expected) {
        assertEquals(expected, config.byteLength());
    }

    @Then("the salt configuration should be rejected")
    public void theSaltConfigurationShouldBeRejected() {
        assertNotNull(rejected, "Expected configuration to be rejected but it was accepted");
    }
}

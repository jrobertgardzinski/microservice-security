package com.jrobertgardzinski.security.config.feature;

import com.jrobertgardzinski.security.domain.validation.password.PasswordPolicyConfig;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PasswordPolicyRules {

    private PasswordPolicyConfig config;

    @Given("a new password policy configuration is initialized")
    public void aNewPasswordPolicyConfigurationIsInitialized() {
        config = PasswordPolicyConfig.builder().build();
    }

    @When("no specific properties are provided")
    public void noSpecificPropertiesAreProvided() {
        // intentionally empty — config already uses defaults
    }

    @Then("the policy should adhere to the default security rules:")
    public void thePolicyShouldAdhereToTheDefaultSecurityRules(Map<String, String> expectedRules) {
        assertEquals(Integer.parseInt(expectedRules.get("minLength")), config.minLength());
        assertEquals(Boolean.parseBoolean(expectedRules.get("requireLowercase")), config.requireLowercase());
        assertEquals(Boolean.parseBoolean(expectedRules.get("requireUppercase")), config.requireUppercase());
        assertEquals(Boolean.parseBoolean(expectedRules.get("requireDigit")), config.requireDigit());
        assertEquals(expectedRules.get("specialChars"), config.specialChars());
    }
}

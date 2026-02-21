package com.jrobertgardzinski.password.feature;

import com.jrobertgardzinski.password.policy.PasswordPolicyConfig;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class PasswordPolicyConfigRules {

    private PasswordPolicyConfig.Builder builder;
    private PasswordPolicyConfig config;

    @Given("a new password policy configuration is initialized")
    public void aNewPasswordPolicyConfigurationIsInitialized() {
        builder = PasswordPolicyConfig.builder();
    }

    @When("no specific properties are provided")
    public void noSpecificPropertiesAreProvided() {
        config = builder.build();
    }

    @When("the min length is set to {int}")
    public void theMinLengthIsSetTo(int minLength) {
        config = builder.minLength(minLength).build();
    }

    @When("special chars are disabled")
    public void specialCharsAreDisabled() {
        config = builder.noSpecialChars().build();
    }

    @Then("the policy should adhere to the default security rules:")
    public void thePolicyShouldAdhereToTheDefaultSecurityRules(Map<String, String> expected) {
        assertEquals(Integer.parseInt(expected.get("minLength")), config.minLength());
        assertEquals(Boolean.parseBoolean(expected.get("requireLowercase")), config.requireLowercase());
        assertEquals(Boolean.parseBoolean(expected.get("requireUppercase")), config.requireUppercase());
        assertEquals(Boolean.parseBoolean(expected.get("requireDigit")), config.requireDigit());
        assertEquals(expected.get("specialChars"), config.specialChars());
    }

    @Then("the policy min length should be {int}")
    public void thePolicyMinLengthShouldBe(int expected) {
        assertEquals(expected, config.minLength());
    }

    @Then("the policy special chars should be empty")
    public void thePolicySpecialCharsShouldBeEmpty() {
        assertTrue(config.specialChars().isBlank());
    }
}

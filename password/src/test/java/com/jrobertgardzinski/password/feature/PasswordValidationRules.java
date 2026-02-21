package com.jrobertgardzinski.password.feature;

import com.jrobertgardzinski.password.domain.PlaintextPassword;
import com.jrobertgardzinski.password.domain.PasswordPolicy;
import com.jrobertgardzinski.password.policy.PasswordPolicyAdapter;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import static org.junit.jupiter.api.Assertions.*;

public class PasswordValidationRules {

    private PasswordPolicy policy;
    private PlaintextPassword accepted;
    private IllegalArgumentException rejected;

    @Given("the default password policy is active")
    public void theDefaultPasswordPolicyIsActive() {
        policy = new PasswordPolicyAdapter();
    }

    @When("the user provides password {string}")
    public void theUserProvidesPassword(String raw) {
        try {
            accepted = PlaintextPassword.of(raw, policy);
            rejected = null;
        } catch (IllegalArgumentException e) {
            accepted = null;
            rejected = e;
        }
    }

    @Then("the password is accepted")
    public void thePasswordIsAccepted() {
        assertNotNull(accepted, "Expected password to be accepted but it was rejected: "
                + (rejected != null ? rejected.getMessage() : ""));
    }

    @Then("the password is rejected with an error containing {string}")
    public void thePasswordIsRejectedWithAnErrorContaining(String fragment) {
        assertNotNull(rejected, "Expected password to be rejected but it was accepted");
        assertTrue(rejected.getMessage().contains(fragment),
                "Expected error to contain '%s' but was: %s".formatted(fragment, rejected.getMessage()));
    }
}

package com.jrobertgardzinski.security.application.feature.input;

import com.jrobertgardzinski.password.domain.PlaintextPassword;
import com.jrobertgardzinski.password.factory.PasswordFactory;
import com.jrobertgardzinski.password.policy.PasswordPolicyAdapter;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import static org.junit.jupiter.api.Assertions.*;

public class PlaintextPasswordRules {

    private final PasswordFactory passwordFactory = new PasswordFactory(new PasswordPolicyAdapter());
    private Exception exception;
    private PlaintextPassword password;

    // rule 0

    @When("I provide no password")
    public void w0() {
        try {
            password = passwordFactory.create(null);
        } catch (Exception e) {
            exception = e;
        }
    }

    // rule 1

    @When("I create a password from {string}")
    public void w1(String value) {
        try {
            password = passwordFactory.create(value);
        } catch (Exception e) {
            exception = e;
        }
    }

    @Then("password creation fails")
    public void tFails() {
        assertNotNull(exception);
    }

    @Then("password creation passes")
    public void tPasses() {
        assertNull(exception);
        assertNotNull(password);
    }
}

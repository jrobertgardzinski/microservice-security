package com.jrobertgardzinski.security.application.feature.input;

import com.jrobertgardzinski.security.domain.factory.PlaintextPasswordFactory;
import com.jrobertgardzinski.security.domain.validation.ConfigurablePasswordPolicyAdapter;
import com.jrobertgardzinski.security.domain.vo.PlaintextPassword;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import static org.junit.jupiter.api.Assertions.*;

public class PlaintextPasswordRules {

    private final PlaintextPasswordFactory plaintextPasswordFactory = new PlaintextPasswordFactory(new ConfigurablePasswordPolicyAdapter());
    private Exception exception;
    private PlaintextPassword password;

    // rule 0

    @When("I provide no password")
    public void w0() {
        try {
            password = plaintextPasswordFactory.create(null);
        } catch (Exception e) {
            exception = e;
        }
    }

    // rule 1

    @When("I create a password from {string}")
    public void w1(String value) {
        try {
            password = plaintextPasswordFactory.create(value);
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

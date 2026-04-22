package com.jrobertgardzinski.security.application.feature.input;

import com.jrobertgardzinski.email.domain.Email;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import static org.junit.jupiter.api.Assertions.*;

public class EmailRules {

    private Exception exception;
    private Email email;

    // rule 0

    @When("I provide no email")
    public void w0() {
        try {
            email = Email.of(null);
        } catch (Exception e) {
            exception = e;
        }
    }

    // rule 1

    @When("I create an email from {string}")
    public void w1(String value) {
        try {
            email = Email.of(value);
        } catch (Exception e) {
            exception = e;
        }
    }

    @Then("email creation fails")
    public void tFails() {
        assertNotNull(exception);
    }

    // rule 2

    @Then("email creation passes")
    public void tPasses() {
        assertNull(exception);
        assertNotNull(email);
    }
}

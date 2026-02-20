package com.jrobertgardzinski.email.usecases.canregister;

import com.jrobertgardzinski.email.domain.Email;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CanRegisterRules {

    private final CanRegister canRegister = new CanRegister(
            Set.of("mailinator.com", "guerrillamail.com", "tempmail.com"),
            Set.of("evil.com")
    );

    private boolean result;

    @When("checking if {string} can register")
    public void w(String email) {
        result = canRegister.isSatisfiedBy(Email.of(email));
    }

    @Then("registration is allowed")
    public void allowed() {
        assertTrue(result);
    }

    @Then("registration is rejected")
    public void rejected() {
        assertFalse(result);
    }
}

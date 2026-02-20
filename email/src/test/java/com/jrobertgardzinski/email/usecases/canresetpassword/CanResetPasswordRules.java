package com.jrobertgardzinski.email.usecases.canresetpassword;

import com.jrobertgardzinski.email.domain.Email;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CanResetPasswordRules {

    private final CanResetPassword canResetPassword = new CanResetPassword();

    private boolean result;

    @When("checking if {string} can reset password")
    public void w(String email) {
        result = canResetPassword.isSatisfiedBy(Email.of(email));
    }

    @Then("password reset is allowed")
    public void allowed() {
        assertTrue(result);
    }

    @Then("password reset is rejected")
    public void rejected() {
        assertFalse(result);
    }
}

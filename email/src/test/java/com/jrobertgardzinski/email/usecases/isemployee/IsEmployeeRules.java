package com.jrobertgardzinski.email.usecases.isemployee;

import com.jrobertgardzinski.email.domain.Email;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class IsEmployeeRules {

    private final IsEmployee isEmployee = new IsEmployee(Set.of("acme.com", "acme.pl"));

    private boolean result;

    @When("checking if {string} is an employee")
    public void w(String email) {
        result = isEmployee.isSatisfiedBy(Email.of(email));
    }

    @Then("it is confirmed")
    public void confirmed() {
        assertTrue(result);
    }

    @Then("it is not confirmed")
    public void notConfirmed() {
        assertFalse(result);
    }
}

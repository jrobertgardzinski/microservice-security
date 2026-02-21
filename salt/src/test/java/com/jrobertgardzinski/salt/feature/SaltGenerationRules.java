package com.jrobertgardzinski.salt.feature;

import com.jrobertgardzinski.salt.domain.Salt;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import static org.junit.jupiter.api.Assertions.*;

public class SaltGenerationRules {

    private Salt first;
    private Salt second;
    private IllegalArgumentException rejected;

    @When("a salt is generated with {int} bytes")
    public void aSaltIsGeneratedWithBytes(int byteLength) {
        first = Salt.generate(byteLength);
    }

    @When("two salts are generated with {int} bytes")
    public void twoSaltsAreGeneratedWithBytes(int byteLength) {
        first = Salt.generate(byteLength);
        second = Salt.generate(byteLength);
    }

    @When("a salt is created with a blank value")
    public void aSaltIsCreatedWithABlankValue() {
        try {
            first = new Salt("  ");
            rejected = null;
        } catch (IllegalArgumentException e) {
            first = null;
            rejected = e;
        }
    }

    @Then("the salt value is not blank")
    public void theSaltValueIsNotBlank() {
        assertFalse(first.value().isBlank());
    }

    @Then("the salts are different")
    public void theSaltsAreDifferent() {
        assertNotEquals(first, second);
    }

    @Then("the salt creation should be rejected")
    public void theSaltCreationShouldBeRejected() {
        assertNotNull(rejected, "Expected salt creation to be rejected but it was accepted");
    }
}

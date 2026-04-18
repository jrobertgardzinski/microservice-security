package com.jrobertgardzinski.security.application.feature.input.policy;

import com.jrobertgardzinski.password.domain.PlaintextPassword;
import com.jrobertgardzinski.password.policy.*;
import com.jrobertgardzinski.util.constraint.ErrorConstraint;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class StrongPasswordPolicyRules {

    private final List<ErrorConstraint<PlaintextPassword>> policy = List.of(
            new _MinLengthConstraint(12),
            new _ContainsLowercaseConstraint(),
            new _ContainsUppercaseConstraint(),
            new _ContainsDigitConstraint(),
            new _ContainsSpecialCharConstraint("#?!")
    );

    private List<String> violations;

    @When("I validate {string} against strong password policy")
    public void w(String password) {
        PlaintextPassword p = PlaintextPassword.of(password);
        violations = policy.stream()
                .filter(c -> !c.isSatisfied(p))
                .map(ErrorConstraint::code)
                .toList();
    }

    @Then("the policy reports {string}")
    public void tReports(String expectedViolation) {
        assertTrue(violations.contains(expectedViolation),
                "Expected violation '%s' but got: %s".formatted(expectedViolation, violations));
    }

    @Then("the policy reports {int} violations")
    public void tCount(int expectedCount) {
        assertEquals(expectedCount, violations.size(),
                "Expected %d violations but got: %s".formatted(expectedCount, violations));
    }

    @Then("the password satisfies the policy")
    public void tSatisfies() {
        assertTrue(violations.isEmpty(),
                "Expected no violations but got: %s".formatted(violations));
    }
}

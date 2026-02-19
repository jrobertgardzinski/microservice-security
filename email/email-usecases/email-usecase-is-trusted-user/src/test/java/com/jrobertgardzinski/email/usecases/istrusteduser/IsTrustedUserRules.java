package com.jrobertgardzinski.email.usecases.istrusteduser;

import com.jrobertgardzinski.email.domain.Email;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class IsTrustedUserRules {

    private final IsTrustedUser isTrustedUser = new IsTrustedUser(
            Set.of("vip@gmail.com", "ceo@random.com"),
            Set.of("partner.com", "trusted-corp.pl")
    );

    private boolean result;

    @When("checking if {string} is a trusted user")
    public void w(String email) {
        result = isTrustedUser.isSatisfiedBy(Email.of(email));
    }

    @Then("it is trusted")
    public void trusted() {
        assertTrue(result);
    }

    @Then("it is not trusted")
    public void notTrusted() {
        assertFalse(result);
    }
}

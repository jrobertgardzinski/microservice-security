package com.jrobertgardzinski.security.application.feature.registration;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.email.policy.CanRegister;
import com.jrobertgardzinski.hash.algorithm.argon2.Argon2HashAlgorithm;
import com.jrobertgardzinski.password.domain.PlaintextPassword;
import com.jrobertgardzinski.password.policy.CreatePasswordHash;
import com.jrobertgardzinski.password.policy.PasswordPolicy;
import com.jrobertgardzinski.password.security.config.MinLength;
import com.jrobertgardzinski.password.security.config.SpecialChars;
import com.jrobertgardzinski.security.application.feature.support.InMemoryUserRepository;
import com.jrobertgardzinski.security.system.registration.Register;
import com.jrobertgardzinski.security.system.registration.RegisterResult;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RegisterSteps {

    private final InMemoryUserRepository users = new InMemoryUserRepository();
    private final Register register = new Register(
            users,
            CanRegister.builder().build(),
            new CreatePasswordHash(
                    new Argon2HashAlgorithm(),
                    new PasswordPolicy(new MinLength(12), new SpecialChars("#?!"))));

    private RegisterResult result;

    @When("the user registers with email {string} and password {string}")
    public void theUserRegisters(String email, String password) {
        result = register.execute(() -> Email.of(email), () -> PlaintextPassword.of(password));
    }

    @Then("the user is registered")
    public void theUserIsRegistered() {
        assertInstanceOf(RegisterResult.Registered.class, result);
    }

    @Then("registration is rejected")
    public void registrationIsRejected() {
        assertInstanceOf(RegisterResult.Rejected.class, result);
    }

    @Then("the email is flagged as {word}")
    public void theEmailIsFlaggedAs(String flag) {
        assertFlag(flag, rejected().emailErrors());
    }

    @Then("the password is flagged as {word}")
    public void thePasswordIsFlaggedAs(String flag) {
        assertFlag(flag, rejected().passwordErrors());
    }

    private RegisterResult.Rejected rejected() {
        return assertInstanceOf(RegisterResult.Rejected.class, result);
    }

    private void assertFlag(String flag, List<String> errors) {
        switch (flag) {
            case "invalid" -> assertFalse(errors.isEmpty(), "expected validation errors, but there were none");
            case "accepted" -> assertTrue(errors.isEmpty(), "expected no validation errors, but got: " + errors);
            default -> throw new IllegalArgumentException("Unknown flag: " + flag);
        }
    }
}

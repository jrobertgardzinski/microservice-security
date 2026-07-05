package com.jrobertgardzinski.security.application.feature.registration;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.email.policy.CanRegister;
import com.jrobertgardzinski.hash.algorithm.argon2.Argon2HashAlgorithm;
import com.jrobertgardzinski.password.domain.PlaintextPassword;
import com.jrobertgardzinski.password.policy.CreatePasswordHash;
import com.jrobertgardzinski.password.policy.PasswordPolicy;
import com.jrobertgardzinski.password.security.config.MinLength;
import com.jrobertgardzinski.password.domain.HashedPassword;
import com.jrobertgardzinski.password.security.config.SpecialChars;
import com.jrobertgardzinski.security.application.feature.support.InMemoryUserRepository;
import com.jrobertgardzinski.security.domain.entity.User;
import com.jrobertgardzinski.security.system.registration.Register;
import com.jrobertgardzinski.security.system.registration.RegisterResult;
import io.cucumber.java.en.Given;
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

    @Given("the EMAIL {string} is already REGISTERED")
    public void theEmailIsAlreadyRegistered(String email) {
        users.save(new User(Email.of(email), new HashedPassword("seed-hash")));
    }

    @When("the USER REGISTERS with EMAIL {string} and password {string}")
    public void theUserRegisters(String email, String password) {
        result = register.execute(() -> Email.of(email), () -> PlaintextPassword.of(password));
    }

    @Then("the USER is REGISTERED")
    public void theUserIsRegistered() {
        assertInstanceOf(RegisterResult.Registered.class, result);
    }

    @Then("REGISTRATION is rejected")
    public void registrationIsRejected() {
        assertInstanceOf(RegisterResult.Rejected.class, result);
    }

    /**
     * At the use-case level the two outcomes ARE distinct — the boundary needs the difference to
     * notify the address owner. Indistinguishability is an HTTP-contract property, asserted by the
     * HTTP glue for this very sentence.
     */
    @Then("REGISTRATION is quietly refused, indistinguishable from a fresh one")
    public void registrationIsQuietlyRefused() {
        assertInstanceOf(RegisterResult.EmailAlreadyTaken.class, result);
    }

    @Then("the EMAIL is flagged as {word}")
    public void theEmailIsFlaggedAs(String flag) {
        assertFlag(flag, rejected().emailErrors().codes());
    }

    @Then("the password is flagged as {word}")
    public void thePasswordIsFlaggedAs(String flag) {
        assertFlag(flag, rejected().passwordErrors().codes());
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

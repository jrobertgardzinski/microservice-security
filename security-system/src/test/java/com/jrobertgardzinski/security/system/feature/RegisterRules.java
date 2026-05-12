package com.jrobertgardzinski.security.system.feature;

import com.jrobertgardzinski.security.domain.event.registration.RegistrationPassedEvent;
import com.jrobertgardzinski.security.system.usecase.RegisterResult;
import com.jrobertgardzinski.security.system.usecase.RegisterUseCase;
import com.jrobertgardzinski.security.system.usecase.RegistrationParser;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class RegisterRules {

    private final InMemoryUserRepository userRepository = new InMemoryUserRepository();
    private final RegisterUseCase registerUseCase = new RegisterUseCase(
            new Register(userRepository, new StubHashAlgorithm()),
            new RegistrationParser(Fixtures.passwordConstraints())
    );

    private RegisterResult result;
    private String preregisteredEmail;

    // Rule 0

    @When("I register with invalid input")
    public void registerWithInvalidInput() {
        result = registerUseCase.execute(Fixtures.INVALID_EMAIL, Fixtures.INVALID_PASSWORD);
    }

    @Then("registration fails on validating input arguments")
    public void registrationFailsOnValidation() {
        assertInstanceOf(RegisterResult.Invalid.class, result);
    }

    @Then("no account is created")
    public void noAccountIsCreated() {
        assertEquals(0, userRepository.size());
    }

    // Rule 1

    @When("I register with valid input")
    public void registerWithValidInput() {
        result = registerUseCase.execute(Fixtures.VALID_EMAIL, Fixtures.VALID_PASSWORD);
    }

    @Then("registration passes")
    public void registrationPasses() {
        var valid = assertInstanceOf(RegisterResult.Valid.class, result);
        assertInstanceOf(RegistrationPassedEvent.class, valid.event());
    }

    @Then("an account is created")
    public void anAccountIsCreated() {
        assertEquals(1, userRepository.size());
    }

    // Rule 2

    @Given("an account is already registered")
    public void anAccountIsAlreadyRegistered() {
        preregisteredEmail = Fixtures.VALID_EMAIL;
        var seedResult = registerUseCase.execute(preregisteredEmail, Fixtures.VALID_PASSWORD);
        var valid = assertInstanceOf(RegisterResult.Valid.class, seedResult,
                "seeding the repository must succeed for Rule 2 to be meaningful");
        assertInstanceOf(RegistrationPassedEvent.class, valid.event());
    }

    @When("I register with the same email and otherwise valid input")
    public void registerWithSameEmail() {
        assertNotNull(preregisteredEmail, "no email seeded — Given step missing");
        result = registerUseCase.execute(preregisteredEmail, Fixtures.ANOTHER_VALID_PASSWORD);
    }

    @Then("registration fails")
    public void registrationFails() {
        boolean success = result instanceof RegisterResult.Valid v
                && v.event() instanceof RegistrationPassedEvent;
        if (success) {
            throw new AssertionError("expected registration to fail, but it passed");
        }
    }

    @Then("no new account is created")
    public void noNewAccountIsCreated() {
        assertEquals(1, userRepository.size());
    }
}

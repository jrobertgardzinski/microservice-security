package com.jrobertgardzinski.security.system.feature.usecase;

import com.jrobertgardzinski.password.policy.*;
import com.jrobertgardzinski.security.domain.event.registration.RegistrationFailedEvent;
import com.jrobertgardzinski.security.domain.event.registration.RegistrationPassedEvent;
import com.jrobertgardzinski.security.domain.repository.UserRepository;
import com.jrobertgardzinski.security.system.factory.RegisterFactory;
import com.jrobertgardzinski.security.system.feature.Register;
import com.jrobertgardzinski.security.system.stub.StubHashAlgorithm;
import com.jrobertgardzinski.security.system.stub.StubUserRepository;
import com.jrobertgardzinski.security.system.usecase.RegisterResult;
import com.jrobertgardzinski.security.system.usecase.RegisterUseCase;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.List;

import static com.jrobertgardzinski.security.system.TestData.VALID_PASSWORD;
import static org.junit.jupiter.api.Assertions.*;

public class RegisterRules {

    private final RegisterUseCase registerUseCase;
    private final UserRepository userRepository;

    private RegisterResult result;

    public RegisterRules(StubUserRepository stubUserRepository, StubHashAlgorithm stubHashAlgorithm) {
        Register register = new Register(stubUserRepository, stubHashAlgorithm);
        RegisterFactory registerFactory = new RegisterFactory(List.of(
                new _MinLengthConstraint(12),
                new _ContainsLowercaseConstraint(),
                new _ContainsUppercaseConstraint(),
                new _ContainsDigitConstraint(),
                new _ContainsSpecialCharConstraint("#?!")));
        this.registerUseCase = new RegisterUseCase(register, registerFactory);
        this.userRepository = stubUserRepository;
    }

    public void when(String email, String password) {
        result = registerUseCase.execute(email, password);
    }

    // rule 0

    @Then("registration fails on validating input arguments")
    public void t0() {
        assertInstanceOf(RegisterResult.Invalid.class, result);
    }

    @Then("there are some email errors")
    public void t0emailSome() {
        RegisterResult.Invalid casted = (RegisterResult.Invalid) result;
        assertTrue(casted.exception().hasEmailErrors());
    }

    @Then("there are no email errors")
    public void t0emailNo() {
        RegisterResult.Invalid casted = (RegisterResult.Invalid) result;
        assertFalse(casted.exception().hasEmailErrors());
    }

    @Then("there are some password errors")
    public void t0passwordSome() {
        RegisterResult.Invalid casted = (RegisterResult.Invalid) result;
        assertTrue(casted.exception().hasPasswordErrors());
    }

    @Then("there are no password errors")
    public void t0passwordNo() {
        RegisterResult.Invalid casted = (RegisterResult.Invalid) result;
        assertFalse(casted.exception().hasPasswordErrors());
    }

    // rule 1

    @When("I register with an email {string} and a password {string}")
    public void w1(String email, String password) {
        when(email, password);
    }

    @Then("registration passes")
    public void t1() {
        assertInstanceOf(RegisterResult.Valid.class, result);
        RegisterResult.Valid valid = (RegisterResult.Valid) result;
        RegistrationPassedEvent event = (RegistrationPassedEvent) valid.event();
        assertTrue(userRepository.existsBy(event.email()));
    }

    // rule 2

    @Given("a user with an email {string} has already been registered")
    public void g2(String email) {
        registerUseCase.execute(email, VALID_PASSWORD);
    }

    @When("I register with an email {string} and any other required valid parameters")
    public void w2(String email) {
        when(email, VALID_PASSWORD);
    }

    @Then("registration fails")
    public void t2() {
        assertInstanceOf(RegisterResult.Valid.class, result);
        RegisterResult.Valid valid = (RegisterResult.Valid) result;
        assertInstanceOf(RegistrationFailedEvent.class, valid.event());
    }
}

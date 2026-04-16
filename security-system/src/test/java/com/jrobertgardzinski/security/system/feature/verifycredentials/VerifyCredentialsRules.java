package com.jrobertgardzinski.security.system.feature.verifycredentials;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.password.domain.HashedPassword;
import com.jrobertgardzinski.password.domain.PlaintextPassword;
import com.jrobertgardzinski.security.domain.entity.User;
import com.jrobertgardzinski.security.domain.event.authentication.AuthenticationEvent;
import com.jrobertgardzinski.security.domain.event.authentication.AuthenticationFailedEvent;
import com.jrobertgardzinski.security.domain.event.authentication.AuthenticationPassedEvent;
import com.jrobertgardzinski.security.domain.vo.Credentials;
import com.jrobertgardzinski.security.system.feature.VerifyCredentials;
import com.jrobertgardzinski.security.system.stub.StubHashAlgorithm;
import com.jrobertgardzinski.security.system.stub.StubUserRepository;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class VerifyCredentialsRules {

    private final VerifyCredentials verifyCredentials;
    private final StubUserRepository userRepository;
    private final StubHashAlgorithm hashAlgorithm;

    private AuthenticationEvent result;

    public VerifyCredentialsRules(StubUserRepository userRepository, StubHashAlgorithm hashAlgorithm) {
        this.userRepository = userRepository;
        this.hashAlgorithm = hashAlgorithm;
        this.verifyCredentials = new VerifyCredentials(userRepository, hashAlgorithm);
    }

    // background

    @Given("the system has a registered account with email {string} and password {string}")
    public void givenRegisteredAccount(String email, String password) {
        Email e = Email.of(email);
        PlaintextPassword p = PlaintextPassword.of(password);
        HashedPassword hashedPassword = hashAlgorithm.hash(p);
        try {
            userRepository.save(new User(UUID.randomUUID(), e, hashedPassword));
        } catch (Exception ex) {
            fail("Failed to save user in background setup");
        }
    }

    // when

    @When("the system receives credentials with email {string} and password {string}")
    public void whenSystemReceivesCredentials(String email, String password) {
        Credentials credentials = new Credentials(
                Email.of(email),
                PlaintextPassword.of(password)
        );
        result = verifyCredentials.apply(credentials);
    }

    // then

    @Then("the verification passes")
    public void thenVerificationPasses() {
        assertInstanceOf(AuthenticationPassedEvent.class, result);
    }

    @Then("the verification fails due to AuthenticationFailedEvent")
    public void thenVerificationFails() {
        assertInstanceOf(AuthenticationFailedEvent.class, result);
    }
}

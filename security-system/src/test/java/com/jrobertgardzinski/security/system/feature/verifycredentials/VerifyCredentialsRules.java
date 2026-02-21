package com.jrobertgardzinski.security.system.feature.verifycredentials;

import com.jrobertgardzinski.password.domain.PasswordHash;
import com.jrobertgardzinski.password.domain.PlaintextPassword;
import com.jrobertgardzinski.password.factory.PasswordFactory;
import com.jrobertgardzinski.password.policy.PasswordPolicyAdapter;
import com.jrobertgardzinski.salt.domain.Salt;
import com.jrobertgardzinski.security.system.feature.VerifyCredentials;
import com.jrobertgardzinski.security.system.stub.StubHashAlgorithm;
import com.jrobertgardzinski.security.system.stub.StubUserRepository;
import com.jrobertgardzinski.security.domain.entity.User;
import com.jrobertgardzinski.security.domain.event.authentication.AuthenticationEvent;
import com.jrobertgardzinski.security.domain.event.authentication.AuthenticationFailedEvent;
import com.jrobertgardzinski.security.domain.event.authentication.AuthenticationPassedEvent;
import com.jrobertgardzinski.security.domain.vo.Credentials;
import com.jrobertgardzinski.security.domain.vo.Email;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import static org.junit.jupiter.api.Assertions.*;

public class VerifyCredentialsRules {

    private final VerifyCredentials verifyCredentials;
    private final StubUserRepository userRepository;
    private final StubHashAlgorithm hashAlgorithm;
    private final PasswordFactory passwordFactory = new PasswordFactory(new PasswordPolicyAdapter());

    private AuthenticationEvent result;

    public VerifyCredentialsRules(StubUserRepository userRepository, StubHashAlgorithm hashAlgorithm) {
        this.userRepository = userRepository;
        this.hashAlgorithm = hashAlgorithm;
        this.verifyCredentials = new VerifyCredentials(userRepository, hashAlgorithm);
    }

    // background

    @Given("the system has a registered account with email {string} and password {string}")
    public void givenRegisteredAccount(String email, String password) {
        Email e = new Email(email);
        PlaintextPassword p = passwordFactory.create(password);
        Salt salt = Salt.generate(16);
        PasswordHash passwordHash = hashAlgorithm.hash(p, salt);
        try {
            userRepository.save(new User(e, passwordHash));
        } catch (Exception ex) {
            fail("Failed to save user in background setup");
        }
    }

    // when

    @When("the system receives credentials with email {string} and password {string}")
    public void whenSystemReceivesCredentials(String email, String password) {
        Credentials credentials = new Credentials(
                new Email(email),
                passwordFactory.create(password)
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

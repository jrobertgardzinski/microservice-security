package com.jrobertgardzinski.security.infrastructure.feature.emailchange;

import com.jrobertgardzinski.CapturingEmailVerificationNotifier;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.micronaut.context.ApplicationContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.runtime.server.EmbeddedServer;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * HTTP glue for {@code change-email.feature}. Black-box: authenticate, request the change, read back
 * the token e-mailed to the new address (via the test notifier), confirm it, then show the user
 * authenticates under the new address and no longer under the old one.
 */
public class HttpChangeEmailSteps {

    private static final String PASSWORD = "StrongPassword1!";

    private EmbeddedServer server;
    private BlockingHttpClient client;

    private String email;
    private String accessToken;
    private String newEmail;
    private HttpResponse<Map> requestResponse;
    private HttpResponse<Map> confirmResponse;

    @Before
    public void startServer() {
        server = ApplicationContext.run(EmbeddedServer.class);
        client = server.getApplicationContext()
                .createBean(HttpClient.class, server.getURL())
                .toBlocking();
    }

    @After
    public void stopServer() {
        if (server != null) {
            server.close();
        }
    }

    @Given("a registered USER {string} with password {string}")
    public void aRegisteredUser(String email, String password) {
        this.email = email;
        HttpResponse<Map> seeded = exchange(HttpRequest.POST("/register", Map.of("email", email, "password", password)));
        assertEquals(HttpStatus.CREATED, seeded.getStatus(), "failed to seed the user");
        verifySeededUser(email);
    }

    @Given("the USER has AUTHENTICATED")
    public void theUserHasAuthenticated() {
        HttpResponse<Map> authenticated = authenticate(email);
        assertEquals(HttpStatus.OK, authenticated.getStatus());
        accessToken = (String) authenticated.getBody(Map.class).orElseThrow().get("accessToken");
    }

    @Given("another ACCOUNT already holds {string}")
    public void anotherAccountAlreadyHolds(String takenEmail) {
        HttpResponse<Map> seeded = exchange(
                HttpRequest.POST("/register", Map.of("email", takenEmail, "password", PASSWORD)));
        assertEquals(HttpStatus.CREATED, seeded.getStatus(), "failed to seed the occupying account");
    }

    @When("the USER requests to CHANGE the EMAIL to {string}")
    public void theUserRequestsToChangeTheEmail(String newEmail) {
        this.newEmail = newEmail;
        requestResponse = exchange(HttpRequest.POST("/account/email/request", Map.of("newEmail", newEmail))
                .header("Authorization", "Bearer " + accessToken));
        assertEquals(HttpStatus.ACCEPTED, requestResponse.getStatus());
    }

    @When("the USER CONFIRMS the EMAIL CHANGE with the token from the link")
    public void confirmsWithTheLinkToken() {
        String linkToken = server.getApplicationContext()
                .getBean(CapturingEmailVerificationNotifier.class).lastTokenFor(newEmail);
        assertNotNull(linkToken, "no verification token was e-mailed to the new address");
        confirmResponse = exchange(HttpRequest.POST("/confirm-email-change", Map.of("token", linkToken)));
    }

    @Then("the CHANGE request is quietly refused, indistinguishable from a fresh one")
    public void quietlyRefused() {
        assertEquals(HttpStatus.ACCEPTED, requestResponse.getStatus());
        assertEquals(Map.of("status", "EMAIL_CHANGE_LINK_SENT"), requestResponse.getBody(Map.class).orElseThrow(),
                "a taken address must answer byte-for-byte like a fresh change request");
    }

    @Then("the owner of {string} is notified by mail")
    public void ownerIsNotified(String takenEmail) {
        org.junit.jupiter.api.Assertions.assertTrue(server.getApplicationContext()
                        .getBean(com.jrobertgardzinski.CapturingRegistrationNoticeNotifier.class)
                        .noticedEmails().contains(takenEmail),
                "the taken address's owner learns someone tried to use it");
    }

    @When("the USER CONFIRMS the EMAIL CHANGE with a garbage token")
    public void confirmsWithGarbageToken() {
        confirmResponse = exchange(HttpRequest.POST("/confirm-email-change", Map.of("token", "garbage-token")));
    }

    @Given("the USER also signs in through {string} as subject {string}")
    public void alsoSignsInThrough(String provider, String subject) {
        federatedIdentities().link(provider, subject, com.jrobertgardzinski.email.domain.Email.of(email));
    }

    @Then("the {string} identity {string} opens the account {string}")
    public void identityOpensTheAccount(String provider, String subject, String email) {
        org.junit.jupiter.api.Assertions.assertEquals(email,
                federatedIdentities().findUserBy(provider, subject)
                        .map(com.jrobertgardzinski.email.domain.Email::value).orElse(null),
                "the federated link follows the account — the subject is the person, not the address");
    }

    private com.jrobertgardzinski.security.domain.repository.FederatedIdentityRepository federatedIdentities() {
        return server.getApplicationContext()
                .getBean(com.jrobertgardzinski.security.domain.repository.FederatedIdentityRepository.class);
    }

    @Then("the USER can AUTHENTICATE as {string}")
    public void canAuthenticateAs(String asEmail) {
        assertEquals(HttpStatus.OK, authenticate(asEmail).getStatus());
    }

    @Then("the USER cannot AUTHENTICATE as {string}")
    public void cannotAuthenticateAs(String asEmail) {
        assertEquals(HttpStatus.UNAUTHORIZED, authenticate(asEmail).getStatus());
    }

    @Then("the EMAIL CHANGE is rejected")
    public void theEmailChangeIsRejected() {
        assertEquals(HttpStatus.BAD_REQUEST, confirmResponse.getStatus());
    }

    private HttpResponse<Map> authenticate(String asEmail) {
        return exchange(HttpRequest.POST("/authenticate", Map.of("email", asEmail, "password", PASSWORD)));
    }

    @SuppressWarnings("unchecked")
    private HttpResponse<Map> exchange(HttpRequest<?> request) {
        try {
            return client.exchange(request, Map.class);
        } catch (HttpClientResponseException e) {
            return (HttpResponse<Map>) e.getResponse();
        }
    }

    private void verifySeededUser(String email) {
        // sign-in requires a verified address, so seeding completes onboarding with the e-mailed token
        String token = server.getApplicationContext()
                .getBean(CapturingEmailVerificationNotifier.class).lastTokenFor(email);
        assertNotNull(token, "no verification link was e-mailed on registration");
        HttpResponse<Map> verified = exchange(HttpRequest.POST("/verify-email", Map.of("token", token)));
        assertEquals(HttpStatus.OK, verified.getStatus(), "failed to verify the seeded user");
    }
}

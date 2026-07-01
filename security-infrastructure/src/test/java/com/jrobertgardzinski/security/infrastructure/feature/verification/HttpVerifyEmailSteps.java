package com.jrobertgardzinski.security.infrastructure.feature.verification;

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
 * HTTP glue for {@code verify-email.feature}. Black-box: request verification, read back the token
 * the app would have e-mailed (via the test notifier), then confirm it — and confirm that a garbage
 * token is refused.
 */
public class HttpVerifyEmailSteps {

    private EmbeddedServer server;
    private BlockingHttpClient client;

    private String email;
    private String linkToken;
    private HttpResponse<Map> response;

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
    }

    @Given("the USER requested EMAIL VERIFICATION")
    public void theUserRequestedEmailVerification() {
        HttpResponse<Map> requested = exchange(HttpRequest.POST("/verify-email/request", Map.of("email", email)));
        assertEquals(HttpStatus.ACCEPTED, requested.getStatus());
        linkToken = server.getApplicationContext()
                .getBean(CapturingEmailVerificationNotifier.class).lastTokenFor(email);
        assertNotNull(linkToken, "no verification token was e-mailed");
    }

    @When("the USER VERIFIES the EMAIL with the VERIFICATION TOKEN from the link")
    public void verifiesWithTheLinkToken() {
        response = exchange(HttpRequest.POST("/verify-email", Map.of("token", linkToken)));
    }

    @When("the USER VERIFIES the EMAIL with a garbage VERIFICATION TOKEN")
    public void verifiesWithGarbageToken() {
        response = exchange(HttpRequest.POST("/verify-email", Map.of("token", "garbage-token")));
    }

    @Then("the EMAIL is verified")
    public void theEmailIsVerified() {
        assertEquals(HttpStatus.OK, response.getStatus());
        assertEquals("EMAIL_VERIFIED", response.getBody(Map.class).orElseThrow().get("status"));
    }

    @Then("the VERIFICATION is rejected")
    public void theVerificationIsRejected() {
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatus());
    }

    @SuppressWarnings("unchecked")
    private HttpResponse<Map> exchange(HttpRequest<?> request) {
        try {
            return client.exchange(request, Map.class);
        } catch (HttpClientResponseException e) {
            return (HttpResponse<Map>) e.getResponse();
        }
    }
}

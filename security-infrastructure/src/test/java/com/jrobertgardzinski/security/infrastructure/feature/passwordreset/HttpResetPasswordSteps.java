package com.jrobertgardzinski.security.infrastructure.feature.passwordreset;

import com.jrobertgardzinski.CapturingPasswordResetNotifier;
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
 * HTTP glue for {@code reset-password.feature}. Black-box: request a reset, read back the token the
 * app would have e-mailed (via the test notifier), set a new password, and show the new password
 * authenticates while the old one no longer does — and that a garbage token is refused.
 */
public class HttpResetPasswordSteps {

    private EmbeddedServer server;
    private BlockingHttpClient client;

    private String email;
    private String linkToken;
    private HttpResponse<Map> resetResponse;

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

    @Given("the USER requested a password RESET")
    public void theUserRequestedAPasswordReset() {
        HttpResponse<Map> requested = exchange(HttpRequest.POST("/reset-password/request", Map.of("email", email)));
        assertEquals(HttpStatus.ACCEPTED, requested.getStatus());
        linkToken = server.getApplicationContext()
                .getBean(CapturingPasswordResetNotifier.class).lastTokenFor(email);
        assertNotNull(linkToken, "no reset token was e-mailed");
    }

    @When("the USER RESETS the password to {string} with the RESET TOKEN from the link")
    public void resetsWithTheLinkToken(String newPassword) {
        resetResponse = exchange(HttpRequest.POST("/reset-password",
                Map.of("token", linkToken, "password", newPassword)));
    }

    @When("the USER RESETS the password to {string} with a garbage RESET TOKEN")
    public void resetsWithGarbageToken(String newPassword) {
        resetResponse = exchange(HttpRequest.POST("/reset-password",
                Map.of("token", "garbage-token", "password", newPassword)));
    }

    @Then("the USER can AUTHENTICATE with {string}")
    public void canAuthenticateWith(String password) {
        assertEquals(HttpStatus.OK, authenticate(password).getStatus());
    }

    @Then("the USER cannot AUTHENTICATE with {string}")
    public void cannotAuthenticateWith(String password) {
        assertEquals(HttpStatus.UNAUTHORIZED, authenticate(password).getStatus());
    }

    @Then("the password RESET is rejected")
    public void thePasswordResetIsRejected() {
        assertEquals(HttpStatus.BAD_REQUEST, resetResponse.getStatus());
    }

    private HttpResponse<Map> authenticate(String password) {
        return exchange(HttpRequest.POST("/authenticate", Map.of("email", email, "password", password)));
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

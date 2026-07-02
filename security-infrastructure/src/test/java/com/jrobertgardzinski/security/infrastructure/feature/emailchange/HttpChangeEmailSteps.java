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
    private String linkToken;
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

    @When("the USER requests to CHANGE the EMAIL to {string}")
    public void theUserRequestsToChangeTheEmail(String newEmail) {
        HttpResponse<Map> requested = exchange(HttpRequest.POST("/account/email/request", Map.of("newEmail", newEmail))
                .header("Authorization", "Bearer " + accessToken));
        assertEquals(HttpStatus.ACCEPTED, requested.getStatus());
        linkToken = server.getApplicationContext()
                .getBean(CapturingEmailVerificationNotifier.class).lastTokenFor(newEmail);
        assertNotNull(linkToken, "no verification token was e-mailed to the new address");
    }

    @When("the USER CONFIRMS the EMAIL CHANGE with the token from the link")
    public void confirmsWithTheLinkToken() {
        confirmResponse = exchange(HttpRequest.POST("/confirm-email-change", Map.of("token", linkToken)));
    }

    @When("the USER CONFIRMS the EMAIL CHANGE with a garbage token")
    public void confirmsWithGarbageToken() {
        confirmResponse = exchange(HttpRequest.POST("/confirm-email-change", Map.of("token", "garbage-token")));
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

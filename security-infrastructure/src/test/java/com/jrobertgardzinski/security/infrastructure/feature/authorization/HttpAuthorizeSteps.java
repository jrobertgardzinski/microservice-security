package com.jrobertgardzinski.security.infrastructure.feature.authorization;

import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.micronaut.context.ApplicationContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.runtime.server.EmbeddedServer;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * HTTP glue for {@code authorize.feature}. Black-box throughout: an access token is obtained by
 * really authenticating, the protected {@code GET /me} is reached with it as a Bearer token, and
 * expiry is produced by advancing the {@code test}-environment clock past the access-token validity.
 */
public class HttpAuthorizeSteps {

    private static final String PASSWORD = "StrongPassword1!";

    private EmbeddedServer server;
    private BlockingHttpClient client;

    private String email;
    private String accessToken;
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

    @Given("the USER has AUTHENTICATED")
    public void theUserHasAuthenticated() {
        HttpResponse<Map> authenticated = exchange(
                HttpRequest.POST("/authenticate", Map.of("email", email, "password", PASSWORD)));
        assertEquals(HttpStatus.OK, authenticated.getStatus());
        accessToken = (String) authenticated.getBody(Map.class).orElseThrow().get("accessToken");
    }

    @When("the ACCESS TOKEN expires")
    public void theAccessTokenExpires() {
        // access tokens are valid for 1h; advance past it
        client.exchange(HttpRequest.POST("/test/clock/advance", Map.of("duration", "PT61M")));
    }

    @When("the USER requests the protected resource with their ACCESS TOKEN")
    public void requestsWithAccessToken() {
        response = getMe("Bearer " + accessToken);
    }

    @When("^the USER requests the protected resource with (no token|a garbage token)$")
    public void requestsWith(String tokenCase) {
        response = switch (tokenCase) {
            case "no token" -> getMe(null);
            case "a garbage token" -> getMe("Bearer not-a-real-token");
            default -> throw new IllegalArgumentException("Unknown token case: " + tokenCase);
        };
    }

    @Then("access is granted")
    public void accessIsGranted() {
        assertEquals(HttpStatus.OK, response.getStatus());
    }

    @Then("access is refused")
    public void accessIsRefused() {
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatus());
    }

    private HttpResponse<Map> getMe(String authorization) {
        MutableHttpRequest<?> request = HttpRequest.GET("/me");
        if (authorization != null) {
            request = request.header("Authorization", authorization);
        }
        return exchange(request);
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

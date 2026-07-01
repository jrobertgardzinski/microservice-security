package com.jrobertgardzinski.security.infrastructure.feature.session;

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
import io.micronaut.http.cookie.Cookie;
import io.micronaut.runtime.server.EmbeddedServer;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The HTTP entry point's glue for {@code refresh-session.feature}. A real embedded server runs per
 * scenario in the {@code test} environment. Everything is black-box: an "active session" is a real
 * authentication whose {@code Set-Cookie} refresh token the client keeps and replays; an "expired"
 * one advances the clock past the refresh-token validity; "no session" simply holds no cookie.
 */
public class HttpRefreshSteps {

    private static final String PASSWORD = "StrongPassword1!";
    private static final String REFRESH_COOKIE = "refresh_token";

    private EmbeddedServer server;
    private BlockingHttpClient client;

    private String email;
    private String refreshCookie; // the refresh token the client holds between requests
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

    @Given("a registered USER {string}")
    public void aRegisteredUser(String email) {
        this.email = email;
        HttpResponse<Map> seeded = post("/register", Map.of("email", email, "password", PASSWORD));
        assertEquals(HttpStatus.CREATED, seeded.getStatus(), "failed to seed the user through the real entry point");
    }

    @Given("the USER has an active session")
    public void theUserHasAnActiveSession() {
        authenticateAndKeepCookie();
    }

    @Given("the USER'S session has expired")
    public void theUsersSessionHasExpired() {
        authenticateAndKeepCookie();
        advanceClockByHours(25); // past the 24h refresh-token validity
    }

    @Given("the USER has no session")
    public void theUserHasNoSession() {
        // registered but never authenticated — the client holds no refresh cookie
    }

    @When("the USER REFRESHES the session")
    public void theUserRefreshesTheSession() {
        MutableHttpRequest<?> request = HttpRequest.POST("/refresh", null);
        if (refreshCookie != null) {
            request = request.cookie(Cookie.of(REFRESH_COOKIE, refreshCookie));
        }
        response = exchange(request);
    }

    @Then("a fresh session is returned")
    public void aFreshSessionIsReturned() {
        assertEquals(HttpStatus.OK, response.getStatus());
    }

    @Then("the REFRESH is rejected because the session has expired")
    public void rejectedAsExpired() {
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatus());
    }

    @Then("the REFRESH is rejected because there is no session to REFRESH")
    public void rejectedAsNotFound() {
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatus());
    }

    // --- Helpers --------------------------------------------------------------

    private void authenticateAndKeepCookie() {
        HttpResponse<Map> authenticated = post("/authenticate", Map.of("email", email, "password", PASSWORD));
        assertEquals(HttpStatus.OK, authenticated.getStatus());
        refreshCookie = authenticated.getCookies().findCookie(REFRESH_COOKIE)
                .map(Cookie::getValue)
                .orElseThrow(() -> new AssertionError("authentication did not set a refresh cookie"));
    }

    private void advanceClockByHours(int hours) {
        client.exchange(HttpRequest.POST("/test/clock/advance", Map.of("duration", "PT" + hours + "H")));
    }

    private HttpResponse<Map> post(String uri, Map<String, String> body) {
        return exchange(HttpRequest.POST(uri, body));
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

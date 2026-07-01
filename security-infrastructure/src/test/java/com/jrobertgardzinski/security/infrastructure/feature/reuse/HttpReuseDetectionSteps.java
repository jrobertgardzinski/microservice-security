package com.jrobertgardzinski.security.infrastructure.feature.reuse;

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
import io.micronaut.http.cookie.Cookie;
import io.micronaut.runtime.server.EmbeddedServer;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * HTTP glue for {@code reuse-detection.feature}. Black-box: authenticate, refresh once (rotating the
 * cookie), then replay the previous cookie — the server must reject it and revoke the whole family,
 * so even the freshly rotated cookie stops working.
 */
public class HttpReuseDetectionSteps {

    private static final String PASSWORD = "StrongPassword1!";
    private static final String REFRESH_COOKIE = "refresh_token";

    private EmbeddedServer server;
    private BlockingHttpClient client;

    private String email;
    private String previousRefreshToken;
    private String currentRefreshToken;
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
        previousRefreshToken = refreshCookieOf(authenticated);
    }

    @Given("the USER has REFRESHED the session once")
    public void theUserHasRefreshedOnce() {
        HttpResponse<Map> refreshed = refreshWith(previousRefreshToken);
        assertEquals(HttpStatus.OK, refreshed.getStatus());
        currentRefreshToken = refreshCookieOf(refreshed);
    }

    @When("the session is REFRESHED again with the previous REFRESH TOKEN")
    public void refreshedWithPreviousToken() {
        response = refreshWith(previousRefreshToken);
    }

    @Then("the REFRESH is rejected")
    public void theRefreshIsRejected() {
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatus());
    }

    @Then("the current REFRESH TOKEN no longer works")
    public void theCurrentRefreshTokenNoLongerWorks() {
        assertEquals(HttpStatus.UNAUTHORIZED, refreshWith(currentRefreshToken).getStatus());
    }

    private HttpResponse<Map> refreshWith(String refreshToken) {
        return exchange(HttpRequest.POST("/refresh", null).cookie(Cookie.of(REFRESH_COOKIE, refreshToken)));
    }

    private static String refreshCookieOf(HttpResponse<?> response) {
        return response.getCookies().findCookie(REFRESH_COOKIE)
                .map(Cookie::getValue)
                .orElseThrow(() -> new AssertionError("no refresh cookie set"));
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

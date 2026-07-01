package com.jrobertgardzinski.security.infrastructure.feature.revoke;

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
 * HTTP glue for {@code revoke-all-sessions.feature}. Black-box: authenticate twice to hold two
 * sessions, POST /sessions/revoke-all with one access token, then show that neither access token
 * authorizes and neither refresh cookie can refresh any more.
 */
public class HttpRevokeAllSessionsSteps {

    private static final String PASSWORD = "StrongPassword1!";
    private static final String REFRESH_COOKIE = "refresh_token";

    private EmbeddedServer server;
    private BlockingHttpClient client;

    private String email;
    private String accessTokenA;
    private String accessTokenB;
    private String refreshCookieA;
    private String refreshCookieB;

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

    @Given("the USER has two active sessions")
    public void theUserHasTwoActiveSessions() {
        Map<String, String> a = authenticate();
        accessTokenA = a.get("token");
        refreshCookieA = a.get("cookie");
        Map<String, String> b = authenticate();
        accessTokenB = b.get("token");
        refreshCookieB = b.get("cookie");
    }

    @When("the USER REVOKES all sessions")
    public void theUserRevokesAllSessions() {
        HttpResponse<Map> revoked = exchange(
                HttpRequest.POST("/sessions/revoke-all", null).header("Authorization", "Bearer " + accessTokenA));
        assertEquals(HttpStatus.OK, revoked.getStatus());
    }

    @Then("neither ACCESS TOKEN authorizes any longer")
    public void neitherAccessTokenAuthorizes() {
        assertEquals(HttpStatus.UNAUTHORIZED, me(accessTokenA).getStatus());
        assertEquals(HttpStatus.UNAUTHORIZED, me(accessTokenB).getStatus());
    }

    @Then("neither REFRESH TOKEN can be REFRESHED")
    public void neitherRefreshTokenCanBeRefreshed() {
        assertEquals(HttpStatus.UNAUTHORIZED, refresh(refreshCookieA).getStatus());
        assertEquals(HttpStatus.UNAUTHORIZED, refresh(refreshCookieB).getStatus());
    }

    private Map<String, String> authenticate() {
        HttpResponse<Map> authenticated = exchange(
                HttpRequest.POST("/authenticate", Map.of("email", email, "password", PASSWORD)));
        assertEquals(HttpStatus.OK, authenticated.getStatus());
        return Map.of(
                "token", (String) authenticated.getBody(Map.class).orElseThrow().get("accessToken"),
                "cookie", authenticated.getCookies().findCookie(REFRESH_COOKIE)
                        .map(Cookie::getValue).orElseThrow(() -> new AssertionError("no refresh cookie")));
    }

    private HttpResponse<Map> me(String accessToken) {
        return exchange(HttpRequest.GET("/me").header("Authorization", "Bearer " + accessToken));
    }

    private HttpResponse<Map> refresh(String refreshCookie) {
        return exchange(HttpRequest.POST("/refresh", null).cookie(Cookie.of(REFRESH_COOKIE, refreshCookie)));
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

package com.jrobertgardzinski.security.infrastructure.feature.logout;

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
 * HTTP glue for {@code logout.feature}. Black-box: authenticate to get a refresh cookie and access
 * token, POST /logout, then show the same cookie can no longer refresh and the same access token no
 * longer authorizes.
 */
public class HttpLogoutSteps {

    private static final String PASSWORD = "StrongPassword1!";
    private static final String REFRESH_COOKIE = "refresh_token";

    private EmbeddedServer server;
    private BlockingHttpClient client;

    private String email;
    private String refreshCookie;
    private String accessToken;
    private HttpResponse<Map> logoutResponse;
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
        refreshCookie = authenticated.getCookies().findCookie(REFRESH_COOKIE)
                .map(Cookie::getValue).orElseThrow(() -> new AssertionError("no refresh cookie"));
    }

    @When("the USER LOGS OUT")
    public void theUserLogsOut() {
        MutableHttpRequest<?> request = HttpRequest.POST("/logout", null);
        if (refreshCookie != null) {
            request = request.cookie(Cookie.of(REFRESH_COOKIE, refreshCookie));
        }
        logoutResponse = exchange(request);
    }

    @When("the USER tries to REFRESH the session")
    public void theUserTriesToRefresh() {
        response = exchange(HttpRequest.POST("/refresh", null).cookie(Cookie.of(REFRESH_COOKIE, refreshCookie)));
    }

    @When("the USER requests the protected resource with the ACCESS TOKEN")
    public void requestsProtectedResource() {
        response = exchange(HttpRequest.GET("/me").header("Authorization", "Bearer " + accessToken));
    }

    @Then("the REFRESH is refused")
    public void theRefreshIsRefused() {
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatus());
    }

    @Then("access is refused")
    public void accessIsRefused() {
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatus());
    }

    @Then("the LOGOUT succeeds")
    public void theLogoutSucceeds() {
        assertEquals(HttpStatus.OK, logoutResponse.getStatus());
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

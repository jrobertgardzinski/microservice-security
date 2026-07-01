package com.jrobertgardzinski.security.infrastructure.feature.listsessions;

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

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * HTTP glue for {@code list-sessions.feature}. Black-box: authenticate a couple of times, then GET
 * /sessions and count the active sessions returned.
 */
public class HttpListSessionsSteps {

    private EmbeddedServer server;
    private BlockingHttpClient client;

    private String email;
    private String accessToken;
    private HttpResponse<Map> listResponse;

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

    @Given("the USER has AUTHENTICATED twice")
    public void theUserHasAuthenticatedTwice() {
        authenticate();
        accessToken = authenticate();
    }

    @When("the USER LISTS their active sessions")
    public void theUserListsTheirActiveSessions() {
        listResponse = exchange(HttpRequest.GET("/sessions").header("Authorization", "Bearer " + accessToken));
    }

    @Then("two active sessions are listed")
    public void twoActiveSessionsAreListed() {
        assertEquals(HttpStatus.OK, listResponse.getStatus());
        List<?> sessions = (List<?>) listResponse.getBody(Map.class).orElseThrow().get("sessions");
        assertEquals(2, sessions.size());
    }

    private String authenticate() {
        HttpResponse<Map> authenticated = exchange(
                HttpRequest.POST("/authenticate", Map.of("email", email, "password", "StrongPassword1!")));
        assertEquals(HttpStatus.OK, authenticated.getStatus());
        return (String) authenticated.getBody(Map.class).orElseThrow().get("accessToken");
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

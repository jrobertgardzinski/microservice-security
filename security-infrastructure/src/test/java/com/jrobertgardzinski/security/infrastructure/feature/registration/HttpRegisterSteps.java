package com.jrobertgardzinski.security.infrastructure.feature.registration;

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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The HTTP entry point's glue for {@code register.feature}. A real embedded server is started per
 * scenario (a fresh application context, hence a fresh in-memory store — clean isolation), and the
 * steps assert purely on the HTTP contract: status codes and the JSON error channels. Same feature,
 * same behaviour, different entry point.
 */
public class HttpRegisterSteps {

    private EmbeddedServer server;
    private BlockingHttpClient client;
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

    @Given("the email {string} is already registered")
    public void theEmailIsAlreadyRegistered(String email) {
        post(email, "StrongPassword1!"); // seed through the real entry point
        assertEquals(HttpStatus.CREATED, response.getStatus(), "failed to seed the existing user");
    }

    @When("the user registers with email {string} and password {string}")
    public void theUserRegisters(String email, String password) {
        post(email, password);
    }

    @Then("the user is registered")
    public void theUserIsRegistered() {
        assertEquals(HttpStatus.CREATED, response.getStatus());
    }

    @Then("registration is rejected")
    public void registrationIsRejected() {
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatus());
    }

    @Then("registration is rejected because the email is already taken")
    public void registrationIsRejectedBecauseEmailAlreadyTaken() {
        assertEquals(HttpStatus.CONFLICT, response.getStatus());
    }

    @Then("the email is flagged as {word}")
    public void theEmailIsFlaggedAs(String flag) {
        assertFlag(flag, errors("emailErrors"));
    }

    @Then("the password is flagged as {word}")
    public void thePasswordIsFlaggedAs(String flag) {
        assertFlag(flag, errors("passwordErrors"));
    }

    @SuppressWarnings("unchecked")
    private void post(String email, String password) {
        HttpRequest<?> request = HttpRequest.POST("/register", Map.of("email", email, "password", password));
        try {
            response = client.exchange(request, Map.class);
        } catch (HttpClientResponseException e) {
            response = (HttpResponse<Map>) e.getResponse();
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> errors(String field) {
        Map<String, Object> body = response.getBody(Map.class).orElseThrow();
        return (List<String>) body.getOrDefault(field, List.of());
    }

    private void assertFlag(String flag, List<String> errors) {
        switch (flag) {
            case "invalid" -> assertFalse(errors.isEmpty(), "expected validation errors, but there were none");
            case "accepted" -> assertTrue(errors.isEmpty(), "expected no validation errors, but got: " + errors);
            default -> throw new IllegalArgumentException("Unknown flag: " + flag);
        }
    }
}

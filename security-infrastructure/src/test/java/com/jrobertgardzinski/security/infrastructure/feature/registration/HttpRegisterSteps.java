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
    private Map freshRegistrationBody;

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

    @Given("the EMAIL {string} is already REGISTERED")
    public void theEmailIsAlreadyRegistered(String email) {
        post(email, "StrongPassword1!"); // seed through the real entry point
        assertEquals(HttpStatus.CREATED, response.getStatus(), "failed to seed the existing user");
        freshRegistrationBody = response.getBody(Map.class).orElseThrow();
    }

    @When("the USER REGISTERS with EMAIL {string} and password {string}")
    public void theUserRegisters(String email, String password) {
        post(email, password);
    }

    @Then("the USER is REGISTERED")
    public void theUserIsRegistered() {
        assertEquals(HttpStatus.CREATED, response.getStatus());
    }

    @Then("REGISTRATION is rejected")
    public void registrationIsRejected() {
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatus());
    }

    @Then("REGISTRATION is quietly refused, indistinguishable from a fresh one")
    public void registrationIsQuietlyRefused() {
        assertEquals(HttpStatus.CREATED, response.getStatus());
        assertEquals(freshRegistrationBody, response.getBody(Map.class).orElseThrow(),
                "the taken-email reply must be byte-for-byte the fresh-registration reply");
    }

    @Then("the EMAIL is flagged as {word}")
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

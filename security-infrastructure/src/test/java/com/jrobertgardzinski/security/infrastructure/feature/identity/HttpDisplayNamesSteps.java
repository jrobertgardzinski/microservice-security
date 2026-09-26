package com.jrobertgardzinski.security.infrastructure.feature.identity;

import com.jrobertgardzinski.CapturingEmailVerificationNotifier;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.micronaut.context.ApplicationContext;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.runtime.server.EmbeddedServer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HTTP glue for {@code display-names.feature}. Black-box: every person is registered, verified
 * and signed in over HTTP, their id is what {@code /me} says, and the names come back from the
 * anonymous {@code GET /users?ids=}.
 */
public class HttpDisplayNamesSteps {

    private static final String PASSWORD = "StrongPassword1!";

    private EmbeddedServer server;
    private BlockingHttpClient client;
    private final Map<String, String> idsByEmail = new HashMap<>();
    private final String nobody = UUID.randomUUID().toString();
    private Map<String, String> answer;

    @Before
    public void startServer() {
        server = ApplicationContext.run(EmbeddedServer.class);
        client = server.getApplicationContext().createBean(HttpClient.class, server.getURL()).toBlocking();
    }

    @After
    public void stopServer() {
        if (server != null) {
            server.close();
        }
    }

    @Given("an account for {string}")
    public void aRegisteredUser(String email) {
        assertEquals(HttpStatus.CREATED,
                exchange(HttpRequest.POST("/register", Map.of("email", email, "password", PASSWORD))).getStatus());
        String token = server.getApplicationContext()
                .getBean(CapturingEmailVerificationNotifier.class).lastTokenFor(email);
        assertNotNull(token, "no verification link was e-mailed on registration");
        assertEquals(HttpStatus.OK, exchange(HttpRequest.POST("/verify-email", Map.of("token", token))).getStatus());
        HttpResponse<Map> authenticated = exchange(
                HttpRequest.POST("/authenticate", Map.of("email", email, "password", PASSWORD)));
        assertEquals(HttpStatus.OK, authenticated.getStatus());
        String accessToken = (String) authenticated.getBody(Map.class).orElseThrow().get("accessToken");
        Map me = exchange(HttpRequest.GET("/me").header("Authorization", "Bearer " + accessToken))
                .getBody(Map.class).orElseThrow();
        idsByEmail.put(email, (String) me.get("id"));
    }

    @When("the names behind the ids of {string} and {string} are looked up")
    public void theNamesBehindTwoIds(String first, String second) {
        lookUp(idsByEmail.get(first) + "," + idsByEmail.get(second));
    }

    @When("the names behind the id of {string} and an id nobody holds are looked up")
    public void theNamesBehindAnIdAndNobody(String email) {
        lookUp(idsByEmail.get(email) + "," + nobody);
    }

    @Then("{string} is shown as {string}")
    public void isShownAs(String email, String name) {
        assertEquals(name, answer.get(idsByEmail.get(email)));
        assertTrue(answer.values().stream().noneMatch(shown -> shown.contains(email.substring(0, email.indexOf('@')))),
                "the local part of an address never leaves security");
    }

    @Then("the id nobody holds is not in the answer")
    public void nobodyIsNotInTheAnswer() {
        assertTrue(!answer.containsKey(nobody));
    }

    private void lookUp(String ids) {
        List<Map> names = client.retrieve(HttpRequest.GET("/users?ids=" + ids), Argument.listOf(Map.class));
        answer = new HashMap<>();
        names.forEach(entry -> answer.put((String) entry.get("id"), (String) entry.get("displayName")));
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

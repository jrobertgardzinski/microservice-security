package com.jrobertgardzinski.security.infrastructure.feature.authentication;

import io.cucumber.datatable.DataTable;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The HTTP entry point's glue for {@code authenticate.feature}. A real embedded server is started
 * per scenario (fresh context &rarr; fresh in-memory stores &rarr; clean isolation), running in the
 * {@code test} environment so the clock is steerable. Everything is driven black-box, through the
 * real entry points: failed attempts are produced by actually POSTing wrong credentials, and the
 * passage of time by POSTing to the clock's control endpoint — never by seeding internal state.
 * Same feature, same behaviour, different entry point.
 */
public class HttpAuthenticateSteps {

    private static final String WRONG_PASSWORD = "WrongButStrongPassword1!";
    private static final String UNKNOWN_EMAIL = "other@example.com";
    private static final Pattern INTEGER = Pattern.compile("\\d+");

    private EmbeddedServer server;
    private BlockingHttpClient client;

    private String email;
    private String password;
    private int maxFailures;
    private int maxBlockMinutes;
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

    // --- Background -----------------------------------------------------------

    @Given("a registered user {string} with password {string}")
    public void aRegisteredUser(String email, String password) {
        this.email = email;
        this.password = password;
        HttpResponse<Map> seeded = post("/register", Map.of("email", email, "password", password));
        assertEquals(HttpStatus.CREATED, seeded.getStatus(), "failed to seed the user through the real entry point");
    }

    @Given("authentication attempts from one source are limited by this policy:")
    public void thePolicy(DataTable policy) {
        // The running service is configured with the default brute-force policy, which equals the
        // values in this table; we read them only to know how many attempts trip the limit and how
        // far to advance the clock to outlast a block. If the table and the service ever drift, the
        // scenario outcomes below go red.
        for (List<String> row : policy.asLists()) {
            String key = row.get(0);
            List<Integer> numbers = integersIn(row.get(1));
            if (key.contains("failed attempts")) {
                maxFailures = numbers.get(0);
            } else if (key.contains("block lasts")) {
                maxBlockMinutes = numbers.get(1);
            }
        }
    }

    // --- Givens ---------------------------------------------------------------

    @Given("the user has reached the failure limit")
    public void reachedFailureLimit() {
        failToAuthenticate(maxFailures);
    }

    @Given("the user has failed to authenticate but stayed under the limit")
    public void underFailureLimit() {
        failToAuthenticate(maxFailures - 1);
    }

    @Given("the source is blocked")
    public void sourceIsBlocked() {
        failToAuthenticate(maxFailures);
        authenticateCorrectly(); // the limit-reaching attempt trips and creates an active block
        assertBlocked();
    }

    // --- Whens ----------------------------------------------------------------

    @When("the user authenticates with the correct credentials")
    public void authenticatesCorrectly() {
        authenticateCorrectly();
    }

    @When("^the user tries to authenticate with (.+)$")
    public void triesToAuthenticateWith(String wrongCredentials) {
        String attemptedEmail;
        String attemptedPassword;
        switch (wrongCredentials) {
            case "the wrong password" -> {
                attemptedEmail = email;
                attemptedPassword = WRONG_PASSWORD;
            }
            case "an unknown email" -> {
                attemptedEmail = UNKNOWN_EMAIL;
                attemptedPassword = password;
            }
            case "a wrong email and password" -> {
                attemptedEmail = UNKNOWN_EMAIL;
                attemptedPassword = WRONG_PASSWORD;
            }
            default -> throw new IllegalArgumentException("Unknown credentials case: " + wrongCredentials);
        }
        response = authenticate(attemptedEmail, attemptedPassword);
    }

    @When("{int} minutes passes")
    public void minutesPass(int minutes) {
        advanceClockByMinutes(minutes);
    }

    @When("the block expires")
    public void theBlockExpires() {
        // a block lasts at most maxBlockMinutes; advancing by that outlasts any block in range
        advanceClockByMinutes(maxBlockMinutes);
    }

    // --- Thens ----------------------------------------------------------------

    @Then("the user is authenticated")
    public void userIsAuthenticated() {
        assertEquals(HttpStatus.OK, response.getStatus());
    }

    @Then("the authentication is rejected")
    public void authenticationIsRejected() {
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatus());
    }

    @Then("the authentication is rejected because the source is blocked")
    public void authenticationIsBlocked() {
        assertBlocked();
    }

    // --- Helpers --------------------------------------------------------------

    private void authenticateCorrectly() {
        response = authenticate(email, password);
    }

    private void failToAuthenticate(int times) {
        for (int i = 0; i < times; i++) {
            HttpResponse<Map> failed = authenticate(email, WRONG_PASSWORD);
            assertEquals(HttpStatus.UNAUTHORIZED, failed.getStatus(), "expected a rejected attempt while building up failures");
        }
    }

    private void assertBlocked() {
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatus());
    }

    private HttpResponse<Map> authenticate(String email, String password) {
        return post("/authenticate", Map.of("email", email, "password", password));
    }

    private void advanceClockByMinutes(int minutes) {
        client.exchange(HttpRequest.POST("/test/clock/advance", Map.of("duration", "PT" + minutes + "M")));
    }

    @SuppressWarnings("unchecked")
    private HttpResponse<Map> post(String uri, Map<String, String> body) {
        try {
            return client.exchange(HttpRequest.POST(uri, body), Map.class);
        } catch (HttpClientResponseException e) {
            return (HttpResponse<Map>) e.getResponse();
        }
    }

    private static List<Integer> integersIn(String text) {
        Matcher matcher = INTEGER.matcher(text);
        List<Integer> numbers = new java.util.ArrayList<>();
        while (matcher.find()) {
            numbers.add(Integer.parseInt(matcher.group()));
        }
        return numbers;
    }
}

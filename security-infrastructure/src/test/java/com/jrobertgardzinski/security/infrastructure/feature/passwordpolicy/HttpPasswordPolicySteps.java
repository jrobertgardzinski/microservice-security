package com.jrobertgardzinski.security.infrastructure.feature.passwordpolicy;

import com.jrobertgardzinski.CapturingEmailVerificationNotifier;
import com.jrobertgardzinski.persistence.InMemorySecuritySettings;
import com.jrobertgardzinski.password.settings.SetMinPasswordLength;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HTTP glue for {@code password-policy.feature}. Black-box: users are really registered and
 * verified, tokens are obtained by authenticating, the length is set via
 * POST /admin/settings/password/min-length (behind a step-up, like every admin hand) and read back
 * via GET. "admin@example.com" is a bootstrap admin (test config). The one thing done behind the
 * API's back is done on purpose: "written at the console" seeds the in-memory settings rung
 * directly, bypassing the value object — which is exactly what a hand at psql does.
 */
public class HttpPasswordPolicySteps {

    private static final String PASSWORD = "StrongPassword1!";
    private static final String ADMIN = "admin@example.com";
    private static final String PATH = "/admin/settings/password/min-length";

    private EmbeddedServer server;
    private BlockingHttpClient client;
    private HttpResponse<Map> response;
    private Map<?, ?> report;

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

    @Given("a registered USER {string} with password {string}")
    public void aRegisteredUser(String email, String password) {
        HttpResponse<Map> seeded = exchange(HttpRequest.POST("/register", Map.of("email", email, "password", password)));
        assertEquals(HttpStatus.CREATED, seeded.getStatus(), "failed to seed the user");
        String token = server.getApplicationContext()
                .getBean(CapturingEmailVerificationNotifier.class).lastTokenFor(email);
        assertNotNull(token, "no verification link was e-mailed on registration");
        assertEquals(HttpStatus.OK, exchange(HttpRequest.POST("/verify-email", Map.of("token", token))).getStatus());
    }

    @Given("the ADMIN has SET the minimum password length to {int}")
    public void theAdminHasSet(int length) {
        response = set(tokenFor(ADMIN), length);
        assertEquals(HttpStatus.OK, response.getStatus(), "the precondition itself was refused");
    }

    @Given("the database row for the minimum password length holds {int}, written at the console")
    public void theDatabaseRowHoldsWrittenAtTheConsole(int value) {
        server.getApplicationContext().getBean(InMemorySecuritySettings.class).put(SetMinPasswordLength.KEY, value);
    }

    @When("the ADMIN SETS the minimum password length to {int}")
    public void theAdminSets(int length) {
        response = set(tokenFor(ADMIN), length);
    }

    @When("{string} tries to SET the minimum password length to {int}")
    public void triesToSet(String caller, int length) {
        response = set(tokenFor(caller), length);
    }

    @When("the ADMIN asks for the minimum password length in force")
    public void theAdminAsks() {
        fetchReport();
    }

    @When("the USER REGISTERS with EMAIL {string} and password {string}")
    public void theUserRegisters(String email, String password) {
        response = exchange(HttpRequest.POST("/register", Map.of("email", email, "password", password)));
    }

    @Then("REGISTRATION is rejected")
    public void registrationIsRejected() {
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatus());
    }

    @Then("the password is flagged as {word}")
    public void thePasswordIsFlaggedAs(String flag) {
        assertTrue(passwordErrors().stream().anyMatch(error -> error.containsKey(flag)),
                "expected password error " + flag + " in " + passwordErrors());
    }

    @Then("the refusal names the minimum length in force, {int}")
    public void theRefusalNamesTheMinimumLength(int minLength) {
        assertTrue(passwordErrors().contains(Map.of("MIN_LENGTH_NOT_MET", minLength)),
                "expected {MIN_LENGTH_NOT_MET: " + minLength + "} in " + passwordErrors());
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> passwordErrors() {
        return (List<Map<String, Object>>) response.getBody(Map.class).orElseThrow().get("passwordErrors");
    }

    @Then("the minimum password length in force is {int}, decided by the {string} source")
    public void theMinimumPasswordLengthInForceIs(int value, String source) {
        fetchReport();
        assertEquals(value, report.get("value"));
        assertEquals(source, report.get("source"));
    }

    @Then("the report says the {string} source was refused holding {int} because {string}")
    public void theReportSaysTheSourceWasRefused(String source, int held, String reason) {
        Object rejected = report.get("rejected");
        assertTrue(rejected instanceof List<?> list
                        && list.contains(Map.of("source", source, "value", held, "reason", reason)),
                "expected the refusal in " + rejected);
    }

    @Then("the request is refused because {string}")
    public void theRequestIsRefusedBecause(String reason) {
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatus());
        assertEquals(reason, response.getBody(Map.class).orElseThrow().get("reason"));
    }

    @Then("the request is forbidden")
    public void requestForbidden() {
        assertEquals(HttpStatus.FORBIDDEN, response.getStatus());
    }

    private void fetchReport() {
        HttpResponse<Map> fetched = exchange(HttpRequest.GET(PATH).header("Authorization", "Bearer " + tokenFor(ADMIN)));
        assertEquals(HttpStatus.OK, fetched.getStatus());
        report = fetched.getBody(Map.class).orElseThrow();
    }

    private String tokenFor(String email) {
        HttpResponse<Map> authed = exchange(HttpRequest.POST("/authenticate", Map.of("email", email, "password", PASSWORD)));
        assertEquals(HttpStatus.OK, authed.getStatus(), "could not authenticate " + email);
        return (String) authed.getBody(Map.class).orElseThrow().get("accessToken");
    }

    private HttpResponse<Map> set(String token, int length) {
        stepUp(token);
        return exchange(HttpRequest.POST(PATH, Map.of("value", length)).header("Authorization", "Bearer " + token));
    }

    /**
     * The policy binds every future password, so setting it takes fresh proof and not merely a
     * live session. Every caller here has a password and no factors, so re-entering the password
     * elevates at once — including the caller who is then refused on their ROLE.
     */
    private void stepUp(String token) {
        HttpResponse<Map> elevated = exchange(HttpRequest.POST("/account/step-up",
                        Map.of("action", "admin-settings", "password", PASSWORD))
                .header("Authorization", "Bearer " + token));
        assertEquals(HttpStatus.OK, elevated.getStatus());
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

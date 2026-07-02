package com.jrobertgardzinski.security.infrastructure.feature.deleteaccount;

import com.jrobertgardzinski.AccountDeletionOrchestrator;
import com.jrobertgardzinski.CapturingEmailVerificationNotifier;
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

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * HTTP glue for {@code delete-account.feature}. The saga's edges that need other services are
 * driven through the orchestrator bean — the same code path the Kafka listener calls — while
 * everything else stays black-box HTTP; the full loop over a real broker runs in the workspace's
 * compose smoke test. Time is steered through the test clock endpoint, so "overdue" is exact.
 */
public class HttpDeleteAccountSteps {

    private EmbeddedServer server;
    private BlockingHttpClient client;

    private String email;
    private String accessToken;

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
        verifySeededUser(email);
    }

    @Given("the USER has AUTHENTICATED")
    public void theUserHasAuthenticated() {
        HttpResponse<Map> authenticated = authenticate("StrongPassword1!");
        assertEquals(HttpStatus.OK, authenticated.getStatus());
        accessToken = (String) authenticated.getBody(Map.class).orElseThrow().get("accessToken");
    }

    @When("the USER requests account DELETION")
    public void theUserRequestsAccountDeletion() {
        HttpResponse<Map> closed = exchange(HttpRequest.POST("/account/delete", null)
                .header("Authorization", "Bearer " + accessToken));
        assertEquals(HttpStatus.ACCEPTED, closed.getStatus());
    }

    @Given("the USER requested account DELETION")
    public void theUserRequestedAccountDeletion() {
        theUserRequestsAccountDeletion();
    }

    @When("the meme service confirms the content purge")
    public void theMemeServiceConfirmsThePurge() {
        // the orchestrator method the Kafka listener calls on a USER_CONTENT_PURGED event
        server.getApplicationContext().getBean(AccountDeletionOrchestrator.class).completePurge(email);
    }

    @When("the content purge does not confirm within the time limit")
    public void thePurgeDoesNotConfirmInTime() {
        client.exchange(HttpRequest.POST("/test/clock/advance", Map.of("duration", "PT3M")));
        server.getApplicationContext().getBean(AccountDeletionOrchestrator.class).compensateOverdue();
    }

    @Then("the email is not yet free to REGISTER")
    public void theEmailIsNotYetFree() {
        HttpResponse<Map> refused = exchange(
                HttpRequest.POST("/register", Map.of("email", email, "password", "StrongPassword1!")));
        assertEquals(HttpStatus.CONFLICT, refused.getStatus());
    }

    @Then("the USER can AUTHENTICATE again with {string}")
    public void canAuthenticateAgainWith(String password) {
        assertEquals(HttpStatus.OK, authenticate(password).getStatus());
    }

    @Then("the access token no longer authorizes")
    public void theAccessTokenNoLongerAuthorizes() {
        HttpResponse<Map> me = exchange(HttpRequest.GET("/me").header("Authorization", "Bearer " + accessToken));
        assertEquals(HttpStatus.UNAUTHORIZED, me.getStatus());
    }

    @Then("the USER cannot AUTHENTICATE with {string}")
    public void cannotAuthenticateWith(String password) {
        assertEquals(HttpStatus.UNAUTHORIZED, authenticate(password).getStatus());
    }

    @Then("the USER can REGISTER again with {string}")
    public void canRegisterAgainWith(String password) {
        HttpResponse<Map> registered = exchange(HttpRequest.POST("/register", Map.of("email", email, "password", password)));
        assertEquals(HttpStatus.CREATED, registered.getStatus());
    }

    private HttpResponse<Map> authenticate(String password) {
        return exchange(HttpRequest.POST("/authenticate", Map.of("email", email, "password", password)));
    }

    @SuppressWarnings("unchecked")
    private HttpResponse<Map> exchange(HttpRequest<?> request) {
        try {
            return client.exchange(request, Map.class);
        } catch (HttpClientResponseException e) {
            return (HttpResponse<Map>) e.getResponse();
        }
    }

    private void verifySeededUser(String email) {
        // sign-in requires a verified address, so seeding completes onboarding with the e-mailed token
        String token = server.getApplicationContext()
                .getBean(CapturingEmailVerificationNotifier.class).lastTokenFor(email);
        assertNotNull(token, "no verification link was e-mailed on registration");
        HttpResponse<Map> verified = exchange(HttpRequest.POST("/verify-email", Map.of("token", token)));
        assertEquals(HttpStatus.OK, verified.getStatus(), "failed to verify the seeded user");
    }
}

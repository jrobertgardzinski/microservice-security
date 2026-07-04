package com.jrobertgardzinski.security.infrastructure.feature.roles;

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
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.runtime.server.EmbeddedServer;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * HTTP glue for {@code roles.feature}. Black-box: users are really registered and verified, tokens
 * are obtained by authenticating, and roles are read from GET /me and set via
 * PUT /admin/users/{email}/roles. "admin@example.com" is a bootstrap admin (test config), so it may
 * grant roles before anyone has persisted an ADMIN grant.
 */
public class HttpRolesSteps {

    private static final String PASSWORD = "StrongPassword1!";
    private static final String ADMIN = "admin@example.com";

    private EmbeddedServer server;
    private BlockingHttpClient client;
    private HttpResponse<Map> response;
    private List<String> roles;

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

    @When("the ADMIN GRANTS {string} the ROLES {string}")
    public void adminGrants(String target, String roleList) {
        response = setRoles(tokenFor(ADMIN), target, roleList);
    }

    @When("{string} tries to GRANT {string} the ROLES {string}")
    public void nonAdminGrants(String caller, String target, String roleList) {
        response = setRoles(tokenFor(caller), target, roleList);
    }

    @When("{string} asks who they are")
    public void asksWhoTheyAre(String email) {
        response = exchange(HttpRequest.GET("/me").header("Authorization", "Bearer " + tokenFor(email)));
        Object raw = response.getBody(Map.class).orElseThrow().get("roles");
        roles = ((List<?>) raw).stream().map(String::valueOf).sorted().collect(Collectors.toList());
    }

    @Then("their ROLES are exactly {string}")
    public void rolesAreExactly(String expected) {
        List<String> want = Arrays.stream(expected.split(",")).map(String::trim).sorted().collect(Collectors.toList());
        assertEquals(want, roles);
    }

    @Then("the request is forbidden")
    public void requestForbidden() {
        assertEquals(HttpStatus.FORBIDDEN, response.getStatus());
    }

    @Then("the request is not found")
    public void requestNotFound() {
        assertEquals(HttpStatus.NOT_FOUND, response.getStatus());
    }

    private String tokenFor(String email) {
        HttpResponse<Map> authed = exchange(HttpRequest.POST("/authenticate", Map.of("email", email, "password", PASSWORD)));
        assertEquals(HttpStatus.OK, authed.getStatus(), "could not authenticate " + email);
        return (String) authed.getBody(Map.class).orElseThrow().get("accessToken");
    }

    private HttpResponse<Map> setRoles(String token, String target, String roleList) {
        List<String> roleValues = Arrays.stream(roleList.split(",")).map(String::trim).collect(Collectors.toList());
        MutableHttpRequest<?> request = HttpRequest.PUT("/admin/users/" + target + "/roles",
                Map.of("roles", roleValues)).header("Authorization", "Bearer " + token);
        return exchange(request);
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

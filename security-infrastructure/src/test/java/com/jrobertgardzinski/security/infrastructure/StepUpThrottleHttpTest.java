package com.jrobertgardzinski.security.infrastructure;

import com.jrobertgardzinski.CapturingEmailVerificationNotifier;
import io.micronaut.context.ApplicationContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.runtime.server.EmbeddedServer;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Step-up runs behind a live session, but each start verifies a password and (for SECOND_FACTORS)
 * mails a code — so it must be rate-limited, or it is a full-speed password oracle and a code
 * mail-bomb (poz. 5). With a cap of one per window, the second step-up is refused with 429.
 *
 * <p>Rate-limited per CALLER, not per source: this endpoint is authenticated, so it knows exactly
 * whose attempt it is. Keyed on the address, one office shares one budget — a colleague mistyping
 * their password locks everyone behind the NAT out of their own accounts, which is a denial of
 * service done to the victims rather than to the attacker.
 */
@Epic("Authentication")
@Feature("Step-up throttle")
class StepUpThrottleHttpTest {

    private static final String PASSWORD = "StrongPassword1!";

    private EmbeddedServer server;
    private BlockingHttpClient client;

    @BeforeEach
    void start() {
        server = ApplicationContext.run(EmbeddedServer.class,
                Map.of("security.step-up.max-per-window", 1), "test");
        client = server.getApplicationContext().createBean(HttpClient.class, server.getURL()).toBlocking();
    }

    @AfterEach
    void stop() {
        if (server != null) {
            server.close();
        }
    }

    @Test
    @DisplayName("a second step-up from the same source is throttled with 429 + Retry-After")
    void second_step_up_is_throttled() {
        String email = "throttled@example.com";
        String token = onboard(email);

        // the first step-up is allowed (a wrong password still counts as one attempt)
        HttpResponse<Map> first = exchange(HttpRequest.POST("/account/step-up",
                        Map.of("action", "delete-account", "password", "WrongButStrong1!"))
                .header("Authorization", "Bearer " + token));
        org.junit.jupiter.api.Assertions.assertNotEquals(HttpStatus.TOO_MANY_REQUESTS, first.getStatus());

        // the second is refused before any password work or code is sent
        HttpResponse<Map> second = exchange(HttpRequest.POST("/account/step-up",
                        Map.of("action", "delete-account", "password", PASSWORD))
                .header("Authorization", "Bearer " + token));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, second.getStatus());
        assertNotNull(second.getHeaders().get("Retry-After"), "a throttled caller is told when to come back");
    }

    @Test
    @DisplayName("one person's spent window is not everybody's: the budget follows the caller")
    void a_colleague_behind_the_same_address_still_gets_their_turn() {
        String impatient = "impatient@example.com";
        String colleague = "colleague@example.com";
        String impatientToken = onboard(impatient);
        String colleagueToken = onboard(colleague);

        // the impatient one spends the whole window (cap is 1) and is refused
        exchange(HttpRequest.POST("/account/step-up",
                        Map.of("action", "delete-account", "password", "WrongButStrong1!"))
                .header("Authorization", "Bearer " + impatientToken));
        HttpResponse<Map> refused = exchange(HttpRequest.POST("/account/step-up",
                        Map.of("action", "delete-account", "password", "WrongButStrong1!"))
                .header("Authorization", "Bearer " + impatientToken));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, refused.getStatus());

        // the colleague is on the same address — the test client has only one — and untouched by it
        HttpResponse<Map> theirTurn = exchange(HttpRequest.POST("/account/step-up",
                        Map.of("action", "delete-account", "password", PASSWORD))
                .header("Authorization", "Bearer " + colleagueToken));
        org.junit.jupiter.api.Assertions.assertNotEquals(HttpStatus.TOO_MANY_REQUESTS, theirTurn.getStatus(),
                "somebody else's spent window must not answer for this caller");
    }

    @Test
    @DisplayName("an elevation that succeeded does not count against the window")
    void proving_yourself_forgives_the_window() {
        String careful = "careful@example.com";
        String token = onboard(careful);

        // one typo, then the right password: the elevation succeeds and the window is theirs again
        exchange(HttpRequest.POST("/account/step-up",
                        Map.of("action", "delete-account", "password", "WrongButStrong1!"))
                .header("Authorization", "Bearer " + token));
        HttpResponse<Map> elevated = exchange(HttpRequest.POST("/account/step-up",
                        Map.of("action", "delete-account", "password", PASSWORD))
                .header("Authorization", "Bearer " + token));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, elevated.getStatus(),
                "with a cap of one, the second attempt is still refused BEFORE it is judged");

        // so raise the cap: what this case is really about is that a SUCCESS is not charged
        server.close();
        server = ApplicationContext.run(EmbeddedServer.class,
                Map.of("security.step-up.max-per-window", 2), "test");
        client = server.getApplicationContext().createBean(HttpClient.class, server.getURL()).toBlocking();
        String freshToken = onboard(careful);

        exchange(HttpRequest.POST("/account/step-up",
                        Map.of("action", "delete-account", "password", "WrongButStrong1!"))
                .header("Authorization", "Bearer " + freshToken));
        HttpResponse<Map> succeeded = exchange(HttpRequest.POST("/account/step-up",
                        Map.of("action", "delete-account", "password", PASSWORD))
                .header("Authorization", "Bearer " + freshToken));
        assertEquals(HttpStatus.OK, succeeded.getStatus());

        HttpResponse<Map> again = exchange(HttpRequest.POST("/account/step-up",
                        Map.of("action", "delete-account", "password", PASSWORD))
                .header("Authorization", "Bearer " + freshToken));
        org.junit.jupiter.api.Assertions.assertNotEquals(HttpStatus.TOO_MANY_REQUESTS, again.getStatus(),
                "the window held two attempts and both are spent — unless proving yourself clears"
                        + " it, which is the rule a correct password already follows everywhere else");
    }

    private String onboard(String email) {
        exchange(HttpRequest.POST("/register", Map.of("email", email, "password", PASSWORD)));
        String verificationToken = server.getApplicationContext()
                .getBean(CapturingEmailVerificationNotifier.class).lastTokenFor(email);
        exchange(HttpRequest.POST("/verify-email", Map.of("token", verificationToken)));
        return (String) exchange(HttpRequest.POST("/authenticate", Map.of("email", email, "password", PASSWORD)))
                .getBody(Map.class).orElseThrow().get("accessToken");
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

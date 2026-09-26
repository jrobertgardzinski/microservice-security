package com.jrobertgardzinski.security.infrastructure;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.identity.UserId;
import com.jrobertgardzinski.password.domain.HashedPassword;
import com.jrobertgardzinski.security.domain.entity.User;
import com.jrobertgardzinski.security.domain.repository.UserRepository;
import io.micronaut.context.ApplicationContext;
import io.micronaut.http.HttpRequest;
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

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** GET /users?ids= at the HTTP boundary: anonymous, masked, silent about ids nobody holds. */
@Epic("Identity")
@Feature("Display names by id")
class DisplayNamesHttpTest {

    private EmbeddedServer server;
    private BlockingHttpClient client;
    private UserId alice;
    private UserId bob;

    @BeforeEach
    void start() {
        server = ApplicationContext.run(EmbeddedServer.class, "test");
        client = server.getApplicationContext().createBean(HttpClient.class, server.getURL()).toBlocking();
        UserRepository users = server.getApplicationContext().getBean(UserRepository.class);
        alice = users.save(new User(Email.of("alice@example.com"), new HashedPassword("hash"))).id();
        bob = users.save(new User(Email.of("bob@example.com"), new HashedPassword("hash"))).id();
    }

    @AfterEach
    void stop() {
        if (server != null) {
            server.close();
        }
    }

    @Test
    @DisplayName("a batch of ids comes back as masked names, in the order asked, without the addresses")
    void names_for_a_batch() {
        String raw = client.retrieve(HttpRequest.GET("/users?ids=" + bob + "," + alice));

        assertEquals("[{\"id\":\"" + bob + "\",\"displayName\":\"b***@example.com\"},"
                + "{\"id\":\"" + alice + "\",\"displayName\":\"a***@example.com\"}]", raw);
        assertFalse(raw.contains("alice@") || raw.contains("bob@"), "the address never leaves security");
    }

    @Test
    @DisplayName("an id nobody holds is simply absent")
    void an_unknown_id_is_left_out() {
        List<Map> names = client.retrieve(
                HttpRequest.GET("/users?ids=" + alice + ",00000000-0000-0000-0000-000000000000"),
                io.micronaut.core.type.Argument.listOf(Map.class));

        assertEquals(1, names.size());
        assertEquals(alice.toString(), names.get(0).get("id"));
    }

    @Test
    @DisplayName("something that is not an id is refused, and so is a batch over the limit")
    void refusals() {
        HttpClientResponseException notAnId = org.junit.jupiter.api.Assertions.assertThrows(
                HttpClientResponseException.class,
                () -> client.exchange(HttpRequest.GET("/users?ids=alice@example.com"), Map.class));
        assertEquals(HttpStatus.BAD_REQUEST, notAnId.getStatus());
        assertEquals("INVALID_ID", notAnId.getResponse().getBody(Map.class).orElseThrow().get("status"));

        String tooMany = IntStream.range(0, 101).mapToObj(i -> UserId.random().toString())
                .collect(Collectors.joining(","));
        HttpClientResponseException overTheLimit = org.junit.jupiter.api.Assertions.assertThrows(
                HttpClientResponseException.class,
                () -> client.exchange(HttpRequest.GET("/users?ids=" + tooMany), Map.class));
        assertEquals(HttpStatus.BAD_REQUEST, overTheLimit.getStatus());
        assertEquals("TOO_MANY_IDS", overTheLimit.getResponse().getBody(Map.class).orElseThrow().get("status"));

        assertEquals(List.of(), client.retrieve(HttpRequest.GET("/users"), io.micronaut.core.type.Argument.listOf(Map.class)));
    }
}

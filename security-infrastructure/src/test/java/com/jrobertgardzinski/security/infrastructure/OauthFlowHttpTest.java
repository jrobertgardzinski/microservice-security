package com.jrobertgardzinski.security.infrastructure;

import com.sun.net.httpserver.HttpServer;
import io.micronaut.context.ApplicationContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.DefaultHttpClientConfiguration;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.runtime.server.EmbeddedServer;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The whole social sign-in dance over the wire, against a fake provider: /oauth/{provider}/start
 * hands the browser a PKCE-armed authorize redirect; /oauth/callback exchanges the code at the
 * provider's token endpoint, validates the id_token (HS256 against the client secret, issuer,
 * audience, nonce) and lands the browser back on the allow-listed return URL with an access token
 * in the fragment and the refresh cookie set. States are single-use; a wrong nonce or an
 * unvouched email never opens a session.
 */
@Epic("Authentication")
@Feature("Federated sign-in")
class OauthFlowHttpTest {

    private static final String CLIENT_SECRET = "test-secret";
    private static final String RETURN_URL = "http://app.example/gallery";

    private HttpServer fakeIdp;
    private final AtomicReference<String> nextIdToken = new AtomicReference<>();
    private EmbeddedServer server;
    private BlockingHttpClient client;

    @BeforeEach
    void start() throws Exception {
        fakeIdp = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        fakeIdp.createContext("/token", exchange -> {
            byte[] body = ("{\"access_token\":\"at-1\",\"token_type\":\"Bearer\",\"id_token\":\""
                    + nextIdToken.get() + "\"}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        fakeIdp.start();
        String idp = "http://localhost:" + fakeIdp.getAddress().getPort();

        server = ApplicationContext.run(EmbeddedServer.class, Map.of(
                "security.oauth.providers.fake.issuer", idp,
                "security.oauth.providers.fake.authorize-url", idp + "/authorize",
                "security.oauth.providers.fake.token-url", idp + "/token",
                "security.oauth.providers.fake.client-id", "test-client",
                "security.oauth.providers.fake.client-secret", CLIENT_SECRET,
                "security.oauth.providers.fake.redirect-uri", "http://security.example/oauth/callback",
                "security.oauth.allowed-return-prefixes", "http://app.example/"), "test");
        DefaultHttpClientConfiguration noRedirects = new DefaultHttpClientConfiguration();
        noRedirects.setFollowRedirects(false);
        client = server.getApplicationContext()
                .createBean(HttpClient.class, server.getURL(), noRedirects).toBlocking();
    }

    @AfterEach
    void stop() {
        if (server != null) {
            server.close();
        }
        if (fakeIdp != null) {
            fakeIdp.stop(0);
        }
    }

    @Test
    @DisplayName("start redirects to the provider PKCE-armed; the callback opens a session and returns to the app")
    void the_full_dance_signs_in() {
        Map<String, String> authorize = startFlow();
        assertEquals("S256", authorize.get("code_challenge_method"));
        assertNotNull(authorize.get("code_challenge"));
        assertNotNull(authorize.get("nonce"));

        nextIdToken.set(idToken(Map.of(
                "iss", issuer(), "aud", "test-client", "sub", "prov-sub-1",
                "email", "dancer@example.com", "email_verified", true,
                "exp", Instant.now().getEpochSecond() + 300, "nonce", authorize.get("nonce"))));
        HttpResponse<?> back = exchange("/oauth/callback?state=" + authorize.get("state") + "&code=c-1");

        assertEquals(HttpStatus.FOUND, back.getStatus());
        String location = back.getHeaders().get("Location");
        assertTrue(location.startsWith(RETURN_URL + "#accessToken="),
                "the access token rides back in the fragment, got: " + location);
        assertTrue(back.getHeaders().getAll("Set-Cookie").stream().anyMatch(c -> c.startsWith("refresh_token=")),
                "the refresh token rides in its usual HttpOnly cookie");

        String accessToken = location.substring((RETURN_URL + "#accessToken=").length());
        HttpResponse<Map> me = exchange(HttpRequest.GET("/me").header("Authorization", "Bearer " + accessToken), Map.class);
        assertEquals(HttpStatus.OK, me.getStatus());
        assertEquals("dancer@example.com", me.getBody(Map.class).orElseThrow().get("email"));

        HttpResponse<?> replayed = exchange("/oauth/callback?state=" + authorize.get("state") + "&code=c-1");
        assertEquals(HttpStatus.BAD_REQUEST, replayed.getStatus(), "a state is single-use");
    }

    @Test
    @DisplayName("a nonce mismatch or an unvouched email bounces back with an error, no session")
    void bad_assertions_do_not_sign_in() {
        Map<String, String> first = startFlow();
        nextIdToken.set(idToken(Map.of(
                "iss", issuer(), "aud", "test-client", "sub", "prov-sub-2",
                "email", "replayed@example.com", "email_verified", true,
                "exp", Instant.now().getEpochSecond() + 300, "nonce", "NOT-THE-NONCE")));
        HttpResponse<?> replayAttempt = exchange("/oauth/callback?state=" + first.get("state") + "&code=c-2");
        assertEquals(HttpStatus.FOUND, replayAttempt.getStatus());
        assertTrue(replayAttempt.getHeaders().get("Location").endsWith("#oauthError=SIGN_IN_FAILED"));

        Map<String, String> second = startFlow();
        nextIdToken.set(idToken(Map.of(
                "iss", issuer(), "aud", "test-client", "sub", "prov-sub-3",
                "email", "shady@example.com", "email_verified", false,
                "exp", Instant.now().getEpochSecond() + 300, "nonce", second.get("nonce"))));
        HttpResponse<?> unvouched = exchange("/oauth/callback?state=" + second.get("state") + "&code=c-3");
        assertEquals(HttpStatus.FOUND, unvouched.getStatus());
        assertTrue(unvouched.getHeaders().get("Location").endsWith("#oauthError=EMAIL_NOT_VOUCHED"));
    }

    @Test
    @DisplayName("a return URL outside the allow-list is refused before anything starts")
    void foreign_return_urls_are_refused() {
        HttpResponse<?> refused = exchange("/oauth/fake/start?return=" + "http://evil.example/");
        assertEquals(HttpStatus.BAD_REQUEST, refused.getStatus());
    }

    // --- Helpers --------------------------------------------------------------

    private Map<String, String> startFlow() {
        HttpResponse<?> redirect = exchange("/oauth/fake/start?return=" + RETURN_URL);
        assertEquals(HttpStatus.FOUND, redirect.getStatus());
        String location = redirect.getHeaders().get("Location");
        assertTrue(location.startsWith(issuer() + "/authorize?"), "unexpected authorize URL: " + location);
        return URI.create(location).getQuery().lines()
                .flatMap(q -> java.util.Arrays.stream(q.split("&")))
                .map(pair -> pair.split("=", 2))
                .collect(Collectors.toMap(kv -> kv[0], kv -> java.net.URLDecoder.decode(kv[1], StandardCharsets.UTF_8)));
    }

    private String issuer() {
        return "http://localhost:" + fakeIdp.getAddress().getPort();
    }

    private String idToken(Map<String, Object> claims) {
        String header = b64(("{\"alg\":\"HS256\",\"typ\":\"JWT\"}").getBytes(StandardCharsets.UTF_8));
        String payload = b64(claims.entrySet().stream()
                .map(e -> "\"" + e.getKey() + "\":" + (e.getValue() instanceof String s ? "\"" + s + "\"" : e.getValue()))
                .collect(Collectors.joining(",", "{", "}")).getBytes(StandardCharsets.UTF_8));
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(CLIENT_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return header + "." + payload + "."
                    + b64(mac.doFinal((header + "." + payload).getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static String b64(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private HttpResponse<?> exchange(String uri) {
        return exchange(HttpRequest.GET(uri), String.class);
    }

    @SuppressWarnings("unchecked")
    private <T> HttpResponse<T> exchange(HttpRequest<?> request, Class<T> type) {
        try {
            return client.exchange(request, type);
        } catch (HttpClientResponseException e) {
            return (HttpResponse<T>) e.getResponse();
        }
    }
}

package com.jrobertgardzinski.security.infrastructure;

import com.jrobertgardzinski.CapturingEmailCodeChannel;
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

/**
 * The whole multi-factor sign-in over the wire: enrol the e-mail factor, then a correct password no
 * longer yields a session (202, a ticket) — the mailed code, submitted against the ticket,
 * completes the chain and returns the same session shape as a single-factor sign-in. A wrong code
 * is refused with tries remaining.
 */
@Epic("Authentication")
@Feature("Multi-factor sign-in")
class MfaHttpTest {

    private static final String PASSWORD = "StrongPassword1!";

    private EmbeddedServer server;
    private BlockingHttpClient client;

    @BeforeEach
    void start() {
        server = ApplicationContext.run(EmbeddedServer.class, "test");
        client = server.getApplicationContext().createBean(HttpClient.class, server.getURL()).toBlocking();
    }

    @AfterEach
    void stop() {
        if (server != null) {
            server.close();
        }
    }

    @Test
    @DisplayName("with the e-mail factor enrolled, sign-in needs the mailed code, and the right code completes it")
    void password_then_code_signs_in() {
        String email = "mfa-user@example.com";
        String token = registerVerifyAuthenticate(email);
        enrolEmailFactor(email, token);

        // password alone no longer signs in — 202 with a ticket, and a fresh code is out
        HttpResponse<Map> first = exchange(HttpRequest.POST("/authenticate", Map.of("email", email, "password", PASSWORD)));
        assertEquals(HttpStatus.ACCEPTED, first.getStatus());
        Map<?, ?> firstBody = first.getBody(Map.class).orElseThrow();
        assertEquals("MFA_REQUIRED", firstBody.get("status"));
        assertEquals("EMAIL_CODE", firstBody.get("nextFactor"));
        String ticket = (String) firstBody.get("mfaTicket");

        // a wrong code is refused, tries remaining
        HttpResponse<Map> wrong = exchange(HttpRequest.POST("/authenticate/factor",
                Map.of("mfaTicket", ticket, "proof", "000000")));
        assertEquals(HttpStatus.UNAUTHORIZED, wrong.getStatus());
        assertEquals("WRONG_CODE", wrong.getBody(Map.class).orElseThrow().get("status"));

        // the mailed code completes the chain — a real session comes back
        String code = codeChannel().lastCodeFor(email);
        HttpResponse<Map> done = exchange(HttpRequest.POST("/authenticate/factor",
                Map.of("mfaTicket", ticket, "proof", code)));
        assertEquals(HttpStatus.OK, done.getStatus());
        String accessToken = (String) done.getBody(Map.class).orElseThrow().get("accessToken");

        HttpResponse<Map> me = exchange(HttpRequest.GET("/me").header("Authorization", "Bearer " + accessToken));
        assertEquals(HttpStatus.OK, me.getStatus());
        assertEquals(email, me.getBody(Map.class).orElseThrow().get("email"));
    }

    @Test
    @DisplayName("TOTP (authenticator app) enrols with an otpauth secret and signs in with a computed code")
    void totp_enrols_and_signs_in() {
        String email = "totp-user@example.com";
        String token = registerVerifyAuthenticate(email);

        // enrol: start returns the secret to scan (no code sent), confirm seals it with a live code
        HttpResponse<Map> start = exchange(HttpRequest.POST("/account/factors/TOTP/enroll/start", Map.of())
                .header("Authorization", "Bearer " + token));
        assertEquals(HttpStatus.ACCEPTED, start.getStatus());
        Map<?, ?> startBody = start.getBody(Map.class).orElseThrow();
        assertEquals("ENROLL_SETUP", startBody.get("status"));
        String secret = secretFromOtpauth((String) startBody.get("display"));

        HttpResponse<Map> confirmed = exchange(HttpRequest.POST("/account/factors/TOTP/enroll/confirm",
                Map.of("code", totp(secret))).header("Authorization", "Bearer " + token));
        assertEquals(HttpStatus.OK, confirmed.getStatus());

        // sign in: password → ticket → a fresh TOTP code → session
        Map<?, ?> first = exchange(HttpRequest.POST("/authenticate",
                Map.of("email", email, "password", PASSWORD))).getBody(Map.class).orElseThrow();
        assertEquals("TOTP", first.get("nextFactor"));
        HttpResponse<Map> done = exchange(HttpRequest.POST("/authenticate/factor",
                Map.of("mfaTicket", first.get("mfaTicket"), "proof", totp(secret))));
        assertEquals(HttpStatus.OK, done.getStatus());
        assertEquals(email, exchange(HttpRequest.GET("/me")
                .header("Authorization", "Bearer " + done.getBody(Map.class).orElseThrow().get("accessToken")))
                .getBody(Map.class).orElseThrow().get("email"));
    }

    private static String secretFromOtpauth(String uri) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("secret=([A-Z2-7]+)").matcher(uri);
        assertEquals(true, m.find(), "no secret in otpauth URI: " + uri);
        return m.group(1);
    }

    /** A TOTP code for the current second — the server runs a frozen test clock, so "now" agrees. */
    private String totp(String base32) {
        try {
            long step = server.getApplicationContext().getBean(java.time.Clock.class).instant().getEpochSecond() / 30;
            byte[] key = base32Decode(base32);
            byte[] data = new byte[8];
            for (int i = 7; i >= 0; i--) { data[i] = (byte) (step & 0xff); step >>= 8; }
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA1");
            mac.init(new javax.crypto.spec.SecretKeySpec(key, "HmacSHA1"));
            byte[] h = mac.doFinal(data);
            int o = h[h.length - 1] & 0x0f;
            int bin = ((h[o] & 0x7f) << 24) | ((h[o + 1] & 0xff) << 16) | ((h[o + 2] & 0xff) << 8) | (h[o + 3] & 0xff);
            return String.format("%06d", bin % 1_000_000);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] base32Decode(String s) {
        String base32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        int buffer = 0, bits = 0;
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        for (char ch : s.replace("=", "").toUpperCase().toCharArray()) {
            int v = base32.indexOf(ch);
            if (v < 0) continue;
            buffer = (buffer << 5) | v; bits += 5;
            if (bits >= 8) { out.write((buffer >> (bits - 8)) & 0xff); bits -= 8; }
        }
        return out.toByteArray();
    }

    // --- Helpers --------------------------------------------------------------

    private String registerVerifyAuthenticate(String email) {
        assertEquals(HttpStatus.CREATED,
                exchange(HttpRequest.POST("/register", Map.of("email", email, "password", PASSWORD))).getStatus());
        String verificationToken = server.getApplicationContext()
                .getBean(CapturingEmailVerificationNotifier.class).lastTokenFor(email);
        assertEquals(HttpStatus.OK,
                exchange(HttpRequest.POST("/verify-email", Map.of("token", verificationToken))).getStatus());
        HttpResponse<Map> authenticated =
                exchange(HttpRequest.POST("/authenticate", Map.of("email", email, "password", PASSWORD)));
        assertEquals(HttpStatus.OK, authenticated.getStatus(), "no factors yet, so the password signs in directly");
        return (String) authenticated.getBody(Map.class).orElseThrow().get("accessToken");
    }

    private void enrolEmailFactor(String email, String token) {
        assertEquals(HttpStatus.ACCEPTED, exchange(HttpRequest.POST("/account/factors/EMAIL_CODE/enroll/start", Map.of())
                .header("Authorization", "Bearer " + token)).getStatus());
        String enrolCode = codeChannel().lastCodeFor(email);
        HttpResponse<Map> confirmed = exchange(HttpRequest.POST("/account/factors/EMAIL_CODE/enroll/confirm",
                        Map.of("code", enrolCode)).header("Authorization", "Bearer " + token));
        assertEquals(HttpStatus.OK, confirmed.getStatus());
        assertEquals("ENROLLED", confirmed.getBody(Map.class).orElseThrow().get("status"));
    }

    private CapturingEmailCodeChannel codeChannel() {
        return server.getApplicationContext().getBean(CapturingEmailCodeChannel.class);
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

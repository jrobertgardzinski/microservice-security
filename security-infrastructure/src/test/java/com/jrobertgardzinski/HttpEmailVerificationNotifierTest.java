package com.jrobertgardzinski;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.vo.token.VerificationToken;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit test of the production notifier: it posts the recipient and the full verification link (base
 * + token) to the email service, with the configured API key. Uses a hand fake of the client, so no
 * server is needed.
 */
class HttpEmailVerificationNotifierTest {

    @Test
    void posts_the_verification_link_and_api_key() {
        AtomicReference<String> sentKey = new AtomicReference<>();
        AtomicReference<Map<String, Object>> sentBody = new AtomicReference<>();
        EmailServiceClient fakeClient = new EmailServiceClient() {
            @Override
            public void sendVerificationLink(String apiKey, Map<String, Object> body) {
                sentKey.set(apiKey);
                sentBody.set(body);
            }

            @Override
            public void sendPasswordResetLink(String apiKey, Map<String, Object> body) {
                throw new AssertionError("wrong endpoint called");
            }
        };
        var notifier = new HttpEmailVerificationNotifier(fakeClient, "secret-key", "https://app/verify?token=");

        notifier.sendVerificationLink(Email.of("user@example.com"), new VerificationToken("abc123"));

        assertEquals("secret-key", sentKey.get());
        assertEquals(Map.of("to", "user@example.com", "link", "https://app/verify?token=abc123"), sentBody.get());
    }
}

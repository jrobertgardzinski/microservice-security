package com.jrobertgardzinski;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jrobertgardzinski.security.domain.port.CodeChannel;
import com.jrobertgardzinski.security.domain.vo.FactorType;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * Production SMS {@link CodeChannel} (outside {@code test}): POSTs the one-time code to
 * microservice-sms, the standalone SMS channel the paddock also uses. A best-effort synchronous
 * send — if the SMS service is down the user simply does not get the code and retries; the
 * challenge is already stored, so nothing is half-committed. The wire contract is the sms service's
 * {@code POST /send {to, subject, body}}.
 */
@Singleton
@Requires(notEnv = "test")
final class HttpSmsCodeChannel implements CodeChannel {

    private static final Logger LOG = LoggerFactory.getLogger(HttpSmsCodeChannel.class);

    /**
     * A hanging provider must not become a hanging sign-in. Both timeouts are deliberate and short:
     * this call sits inside the user's request, and the fallback — "no code arrived, try again" —
     * is far better than a request thread parked until the socket gives up on its own, which for a
     * default {@link HttpClient} is never.
     */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    private final ObjectMapper json = new ObjectMapper();
    private final String smsUrl;
    private final String apiKey;

    HttpSmsCodeChannel(@Value("${security.sms.url:`http://localhost:8088`}") String smsUrl,
                       @Value("${security.sms.api-key:}") String apiKey) {
        this.smsUrl = smsUrl;
        this.apiKey = apiKey;
    }

    @Override
    public FactorType servesFactor() {
        return FactorType.SMS_CODE;
    }

    @Override
    public void sendCode(String target, String code) {
        String body;
        try {
            // Jackson, not concatenation. The old string was assembled from a phone number that no
            // layer validates — the domain treats it as free text — so a quote or a backslash in it
            // produced a body the sms service answers 400 to, and a crafted one could add fields to
            // the request this service believes it is making.
            body = json.writeValueAsString(Map.of(
                    "to", target,
                    "subject", "Sign-in code",
                    "body", "Your sign-in code is " + code));
        } catch (JsonProcessingException impossible) {
            LOG.warn("could not render the SMS request; the code will not be delivered");
            return;
        }
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(smsUrl + "/send"))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            if (!apiKey.isBlank()) {
                request.header("X-Api-Key", apiKey);
            }
            HttpResponse<Void> response = http.send(request.build(), HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() >= 300) {
                // the status was discarded along with the body until now, so a channel that had been
                // refusing every message for weeks would have looked exactly like one that worked
                LOG.warn("the SMS channel refused the code with HTTP {}", response.statusCode());
            }
        } catch (Exception smsDown) {
            // best-effort: a failed send leaves the user without a code to enter — they retry
            LOG.warn("the SMS channel did not accept the code: {}", smsDown.toString());
            if (smsDown instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }
}

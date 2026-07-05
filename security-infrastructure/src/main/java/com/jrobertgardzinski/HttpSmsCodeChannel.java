package com.jrobertgardzinski;

import com.jrobertgardzinski.security.domain.port.CodeChannel;
import com.jrobertgardzinski.security.domain.vo.FactorType;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

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

    private final HttpClient http = HttpClient.newHttpClient();
    private final String smsUrl;

    HttpSmsCodeChannel(@Value("${security.sms.url:`http://localhost:8088`}") String smsUrl) {
        this.smsUrl = smsUrl;
    }

    @Override
    public FactorType servesFactor() {
        return FactorType.SMS_CODE;
    }

    @Override
    public void sendCode(String target, String code) {
        String body = "{\"to\":\"" + target + "\",\"subject\":\"Sign-in code\",\"body\":\"Your sign-in code is "
                + code + "\"}";
        try {
            http.send(HttpRequest.newBuilder(URI.create(smsUrl + "/send"))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                            .build(),
                    HttpResponse.BodyHandlers.discarding());
        } catch (Exception smsDown) {
            // best-effort: a failed send leaves the user without a code to enter — they retry
            if (smsDown instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }
}

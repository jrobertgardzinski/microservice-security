package com.jrobertgardzinski;

import jakarta.inject.Singleton;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The sign-ins currently out at a provider, keyed by the {@code state} we minted for them. The
 * state ties the provider's callback to the browser that started the dance (CSRF), and the flow
 * it recalls carries the PKCE verifier and nonce that make the code exchange honest. Single-use,
 * short-lived, in memory — a lost entry only means the user clicks the button again.
 */
@Singleton
final class OauthFlowStore {

    static final Duration TTL = Duration.ofMinutes(10);

    record PendingFlow(String provider, String codeVerifier, String nonce, String returnUrl, Instant expires) {}

    private static final SecureRandom RANDOM = new SecureRandom();

    private final Map<String, PendingFlow> byState = new ConcurrentHashMap<>();
    private final Clock clock;

    OauthFlowStore(Clock clock) {
        this.clock = clock;
    }

    String begin(String provider, String codeVerifier, String nonce, String returnUrl) {
        String state = randomToken();
        byState.put(state, new PendingFlow(provider, codeVerifier, nonce, returnUrl,
                clock.instant().plus(TTL)));
        return state;
    }

    Optional<PendingFlow> consume(String state) {
        return Optional.ofNullable(byState.remove(state))
                .filter(flow -> clock.instant().isBefore(flow.expires()));
    }

    static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}

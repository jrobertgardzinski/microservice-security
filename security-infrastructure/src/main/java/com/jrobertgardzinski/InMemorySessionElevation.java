package com.jrobertgardzinski;

import com.jrobertgardzinski.security.system.mfa.SessionElevation;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link SessionElevation}: an access token is "recently re-proven" until a short TTL,
 * and the mark is cleared the first time it is consumed (one-shot). Not durable on purpose — a
 * restart just means stepping up again.
 */
@Singleton
final class InMemorySessionElevation implements SessionElevation {

    private final Map<String, Instant> elevatedUntil = new ConcurrentHashMap<>();
    private final Clock clock;
    private final Duration ttl;

    InMemorySessionElevation(Clock clock,
                             @Value("${security.step-up.ttl-minutes:5}") int ttlMinutes) {
        this.clock = clock;
        this.ttl = Duration.ofMinutes(ttlMinutes);
    }

    @Override
    public void elevate(String accessToken) {
        elevatedUntil.put(accessToken, clock.instant().plus(ttl));
    }

    @Override
    public boolean consume(String accessToken) {
        Instant until = elevatedUntil.remove(accessToken);
        return until != null && clock.instant().isBefore(until);
    }
}

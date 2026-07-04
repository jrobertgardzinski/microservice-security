package com.jrobertgardzinski.security.system.registration;

import com.jrobertgardzinski.security.domain.vo.IpAddress;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-source rate limit on registration: at most {@code maxPerWindow} attempts from one
 * {@link IpAddress} in a rolling {@code window}. Registration is expensive (it hashes a password
 * with Argon2) and creates accounts, so an unthrottled endpoint is both a CPU-exhaustion vector
 * and a mass-signup vector — this caps both without touching the legitimate one-off user. A fixed
 * window in memory; the source is the spoof-resistant IP the resolver already trusts.
 */
public class RegistrationThrottle {

    /** Whether the attempt is allowed, and if not, how long until the window frees up. */
    public record Decision(boolean allowed, long retryAfterSeconds) {}

    private record Window(Instant start, int count) {}

    private final int maxPerWindow;
    private final Duration window;
    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RegistrationThrottle(int maxPerWindow, Duration window, Clock clock) {
        this.maxPerWindow = maxPerWindow;
        this.window = window;
        this.clock = clock;
    }

    /** Record one registration attempt from this source and decide whether it may proceed. */
    public Decision check(IpAddress source) {
        if (maxPerWindow <= 0) {
            return new Decision(true, 0);   // disabled
        }
        Instant now = clock.instant();
        Window updated = windows.compute(source.value(), (ip, current) ->
                current == null || Duration.between(current.start(), now).compareTo(window) >= 0
                        ? new Window(now, 1)
                        : new Window(current.start(), current.count() + 1));
        if (updated.count() <= maxPerWindow) {
            return new Decision(true, 0);
        }
        long retryAfter = Math.max(1,
                window.minus(Duration.between(updated.start(), now)).toSeconds());
        return new Decision(false, retryAfter);
    }
}

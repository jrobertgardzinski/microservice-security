package com.jrobertgardzinski.security.system.throttle;

import com.jrobertgardzinski.security.domain.vo.IpAddress;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-source rate limit for the expensive anonymous endpoints: at most {@code maxPerWindow}
 * attempts from one {@link IpAddress} in a rolling {@code window}. Registration hashes a password
 * with Argon2 and creates accounts; the reset-password and verify-email requests mint tokens and
 * send mails — unthrottled, each is a CPU-exhaustion, mass-signup or mail-bomb vector. One
 * instance guards one endpoint (each gets its own window and cap), so a burst against one cannot
 * starve another. A fixed window in memory; the source is the spoof-resistant IP the resolver
 * already trusts. Deliberately separate from the authentication guard: that one defends accounts
 * against password guessing, this one defends the service against volume.
 */
public class SourceThrottle {

    /** Whether the attempt is allowed, and if not, how long until the window frees up. */
    public record Decision(boolean allowed, long retryAfterSeconds) {}

    private record Window(Instant start, int count) {}

    private final int maxPerWindow;
    private final Duration window;
    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public SourceThrottle(int maxPerWindow, Duration window, Clock clock) {
        this.maxPerWindow = maxPerWindow;
        this.window = window;
        this.clock = clock;
    }

    /** Record one attempt from this source and decide whether it may proceed. */
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

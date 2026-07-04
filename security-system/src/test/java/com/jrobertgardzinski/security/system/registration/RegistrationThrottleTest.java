package com.jrobertgardzinski.security.system.registration;

import com.jrobertgardzinski.security.domain.vo.IpAddress;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Epic("Registration")
@Feature("Per-source throttle")
class RegistrationThrottleTest {

    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-07-04T10:00:00Z"));
    private final Clock clock = new Clock() {
        public Instant instant() { return now.get(); }
        public ZoneOffset getZone() { return ZoneOffset.UTC; }
        public Clock withZone(java.time.ZoneId zone) { return this; }
    };

    private final IpAddress ip = new IpAddress("203.0.113.7");
    private final IpAddress other = new IpAddress("203.0.113.8");

    @Test
    @DisplayName("attempts within the cap pass; the one over the cap is refused with a retry-after")
    void caps_per_source() {
        RegistrationThrottle throttle = new RegistrationThrottle(3, Duration.ofMinutes(15), clock);
        for (int i = 0; i < 3; i++) {
            assertTrue(throttle.check(ip).allowed(), "attempt " + i + " should pass");
        }
        RegistrationThrottle.Decision blocked = throttle.check(ip);
        assertFalse(blocked.allowed(), "the fourth in the window is refused");
        assertTrue(blocked.retryAfterSeconds() > 0, "a refusal says when to come back");
        assertTrue(throttle.check(other).allowed(), "a different source is unaffected");
    }

    @Test
    @DisplayName("the window rolls: once it passes, the source is clear again")
    void window_rolls() {
        RegistrationThrottle throttle = new RegistrationThrottle(1, Duration.ofMinutes(15), clock);
        assertTrue(throttle.check(ip).allowed());
        assertFalse(throttle.check(ip).allowed());
        now.set(now.get().plus(Duration.ofMinutes(16)));
        assertTrue(throttle.check(ip).allowed(), "a fresh window frees the source");
    }

    @Test
    @DisplayName("zero disables the throttle")
    void zero_disables() {
        RegistrationThrottle throttle = new RegistrationThrottle(0, Duration.ofMinutes(15), clock);
        for (int i = 0; i < 100; i++) {
            assertTrue(throttle.check(ip).allowed());
        }
    }
}

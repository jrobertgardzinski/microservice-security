package com.jrobertgardzinski.security.application.feature.support;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * A {@link Clock} whose instant can be moved forward, so scenarios can express
 * "14 minutes later" / "the block expires" deterministically.
 */
public final class MutableClock extends Clock {

    private Instant instant;
    private final ZoneId zone;

    public MutableClock(Instant instant, ZoneId zone) {
        this.instant = instant;
        this.zone = zone;
    }

    public void advanceMinutes(long minutes) {
        instant = instant.plus(minutes, ChronoUnit.MINUTES);
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new MutableClock(instant, zone);
    }

    @Override
    public Instant instant() {
        return instant;
    }
}

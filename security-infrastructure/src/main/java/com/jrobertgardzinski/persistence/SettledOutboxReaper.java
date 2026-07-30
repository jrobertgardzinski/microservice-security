package com.jrobertgardzinski.persistence;

import io.micronaut.context.annotation.Requires;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Retention for the {@code outbox_events} table.
 *
 * <p>Nothing ever removed a drained row: the publisher stamped {@code published_at} and moved on, so
 * the table only grew. Every row carries the subject's e-mail address TWICE — as {@code event_key}
 * and inside the payload — which made it, in practice, a permanent register of every address this
 * service has ever seen: verification mails, password resets, MFA codes, and the account-deletion
 * fact together with the purge rules the leaver chose. A user who exercised their right to be
 * forgotten was erased from {@code users} and left here, in full, for good.
 *
 * <p>This is not a compromise anybody argued for: the same team gave
 * {@code processed_offboarding_outcomes} a seven-day retention, in
 * {@link JdbcProcessedOutcomes#forgetOldOutcomes()}, for a table holding far less. The gap was an
 * omission, and this class is the missing half.
 *
 * <p><strong>Why the window is a week.</strong> A settled row has no operational job left — the
 * event is either with the broker or permanently refused — so the only reason to keep it is looking
 * at it. A week outlives every outage and every re-announcement (offboarding's sweeper republishes
 * until its own mark lands, in minutes), and it matches the sibling reaper so an operator has one
 * number to remember rather than two.
 *
 * <p><strong>What is deliberately never swept.</strong> A row with no {@code published_at} and no
 * {@code failed_at} — an event still waiting for the drain. The query relies on
 * {@code NULL < cutoff} being unknown rather than true, so retention cannot turn into a second,
 * silent way of losing an event no matter how long the broker stays down.
 */
@Singleton
@Requires(beans = DataSource.class)
class SettledOutboxReaper {

    private static final Logger LOG = LoggerFactory.getLogger(SettledOutboxReaper.class);

    /** How long a published — or permanently failed — outbox row is kept for inspection. */
    private static final Duration KEPT_FOR = Duration.ofDays(7);

    private final OutboxEventJdbcRepository events;
    private final Clock clock;

    SettledOutboxReaper(OutboxEventJdbcRepository events, Clock clock) {
        this.events = events;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = "6h", initialDelay = "12m")
    void reap() {
        try {
            Instant cutoff = Instant.now(clock).minus(KEPT_FOR);
            int deleted = events.deleteSettledBefore(cutoff);
            if (deleted > 0) {
                LOG.info("dropped {} settled outbox event(s) older than {}", deleted, KEPT_FOR);
            }
        } catch (RuntimeException reapFailed) {
            // swallowed deliberately: most schedulers stop invoking a task whose run threw, and
            // retiring retention after one bad database moment is how a table grows for ever
            LOG.error("could not drop settled outbox events — the rows stay, the next pass retries",
                    reapFailed);
        }
    }
}

package com.jrobertgardzinski.persistence;

import io.micronaut.context.annotation.Requires;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Deletes email-change tickets nobody ever confirmed.
 *
 * <p>A row is written when somebody STARTS moving their account and removed when they confirm, so
 * every abandoned attempt stayed for ever — and starting one costs a single request. The table is
 * the last one in this service without a retention rule, which is how it also turned out to be the
 * last one whose token had no expiry at all.
 *
 * <p>Retention is deliberately LONGER than the window {@code ConfirmEmailChange} judges by. Expiry
 * is a decision and belongs in one place; this only sweeps up what could not possibly matter any
 * more, so shortening the window in configuration never depends on when a sweep last ran.
 */
@Singleton
@Requires(beans = DataSource.class)
class AbandonedEmailChangeReaper {

    private static final Logger LOG = LoggerFactory.getLogger(AbandonedEmailChangeReaper.class);

    private static final Duration RETENTION = Duration.ofDays(7);

    private final EmailChangeJdbcRepository changes;
    private final Clock clock;

    AbandonedEmailChangeReaper(EmailChangeJdbcRepository changes, Clock clock) {
        this.changes = changes;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = "1h", initialDelay = "5m")
    void reap() {
        try {
            LocalDateTime cutoff = LocalDateTime.now(clock).minus(RETENTION);
            int deleted = changes.deleteByStartedAtBefore(cutoff);
            if (deleted > 0) {
                LOG.info("dropped {} unconfirmed email change(s) started before {}", deleted, cutoff);
            }
        } catch (RuntimeException reapFailed) {
            LOG.warn("email-change retention sweep failed; will try again on the next tick", reapFailed);
        }
    }
}

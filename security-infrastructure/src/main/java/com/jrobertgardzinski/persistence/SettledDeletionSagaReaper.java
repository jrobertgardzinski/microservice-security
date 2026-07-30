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
 * Retention for the {@code account_deletion_sagas} table.
 *
 * <p>The table whose entire purpose is running "delete my account" was the one that kept the address
 * for ever: nothing deleted a row, so a COMPLETED saga sat there, e-mail column and all, long after
 * the account it named was gone. A year later a database dump still answered the question the
 * deletion was supposed to erase — who asked to leave, and when.
 *
 * <p><strong>Why {@code state <> 'STARTED'}.</strong> A running saga is the one row that must
 * survive at any age: {@code AccountDeletionOrchestrator#compensateOverdue} finds accounts to unlock
 * by scanning exactly those rows, so sweeping one would strand an account locked for ever. Only a
 * saga that has reached a verdict — COMPLETED or COMPENSATED — is history, and its age is measured
 * on {@code updated_at}, the column that verdict wrote.
 *
 * <p><strong>Why a week is long enough.</strong> The one thing that still reads a settled saga is
 * {@code lastSagaWasCompensated}, which turns a late purge confirmation into the
 * "CONTENT ERASED AFTER COMPENSATION" alarm. That race is bounded by the portal's retry budget —
 * minutes — so a week outlives it by four orders of magnitude, and matches the window the other two
 * reapers in this package already use.
 */
@Singleton
@Requires(beans = DataSource.class)
class SettledDeletionSagaReaper {

    private static final Logger LOG = LoggerFactory.getLogger(SettledDeletionSagaReaper.class);

    /** How long a saga that has reached its verdict is kept. */
    private static final Duration KEPT_FOR = Duration.ofDays(7);

    private final AccountDeletionSagaJdbcRepository sagas;
    private final Clock clock;

    SettledDeletionSagaReaper(AccountDeletionSagaJdbcRepository sagas, Clock clock) {
        this.sagas = sagas;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = "6h", initialDelay = "14m")
    void reap() {
        try {
            Instant cutoff = Instant.now(clock).minus(KEPT_FOR);
            int deleted = sagas.deleteSettledBefore(cutoff);
            if (deleted > 0) {
                LOG.info("dropped {} settled account-deletion saga(s) older than {}", deleted, KEPT_FOR);
            }
        } catch (RuntimeException reapFailed) {
            // swallowed deliberately: a scheduler that stops on a throw would retire retention silently
            LOG.error("could not drop settled account-deletion sagas — the rows stay, the next pass retries",
                    reapFailed);
        }
    }
}

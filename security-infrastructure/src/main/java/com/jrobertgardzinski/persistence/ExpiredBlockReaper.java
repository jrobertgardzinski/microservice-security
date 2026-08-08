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
 * Deletes blocks that have served their time.
 *
 * <p>The block already stops mattering the moment it expires — the guard asks whether it is STILL
 * ACTIVE — so this changes no decision. It changes what the table is: without it, one row per
 * address ever blocked, kept for ever, which is both a growing table nobody ever shrinks and a
 * permanent list of addresses that once got a password wrong three times. Every other table in this
 * service that fills up on its own already had a reaper; this one was missed because expiry was
 * enforced in the READ, so nothing ever looked wrong.
 *
 * <p>The grace is generous on purpose: a row deleted a moment too early would let a blocked address
 * start counting from zero, so waiting a day past expiry costs nothing and removes that edge
 * entirely.
 */
@Singleton
@Requires(beans = DataSource.class)
class ExpiredBlockReaper {

    private static final Logger LOG = LoggerFactory.getLogger(ExpiredBlockReaper.class);

    private static final Duration GRACE = Duration.ofDays(1);

    private final AuthenticationBlockJdbcRepository blocks;
    private final Clock clock;

    ExpiredBlockReaper(AuthenticationBlockJdbcRepository blocks, Clock clock) {
        this.blocks = blocks;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = "1h", initialDelay = "5m")
    void reap() {
        try {
            LocalDateTime cutoff = LocalDateTime.now(clock).minus(GRACE);
            int deleted = blocks.deleteByExpiryDateBefore(cutoff);
            if (deleted > 0) {
                LOG.info("dropped {} authentication block(s) that expired before {}", deleted, cutoff);
            }
        } catch (RuntimeException reapFailed) {
            LOG.warn("block retention sweep failed; will try again on the next tick", reapFailed);
        }
    }
}

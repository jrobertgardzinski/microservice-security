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
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Singleton
@Requires(beans = DataSource.class)
class JdbcProcessedOutcomes implements ProcessedOutcomes {

    private static final Logger LOG = LoggerFactory.getLogger(JdbcProcessedOutcomes.class);

    /**
     * How long a claimed outcome is remembered.
     *
     * <p>Long enough to outlive every re-announcement: offboarding's sweeper re-publishes an
     * unmarked outcome every pass, and it only stops when the announcement finally sticks. A week
     * is far past any outage that leaves the mark unwritten, and the table is one short row per
     * completed deletion — the alternative, remembering for ever, is a table nobody prunes.
     */
    private static final Duration REMEMBERED_FOR = Duration.ofDays(7);

    private final ProcessedOutcomeJdbcRepository outcomes;
    private final Clock clock;

    JdbcProcessedOutcomes(ProcessedOutcomeJdbcRepository outcomes, Clock clock) {
        this.outcomes = outcomes;
        this.clock = clock;
    }

    @Override
    public boolean claim(String outcomeId, String outcomeType, Instant at) {
        return outcomes.claim(outcomeId, outcomeType,
                LocalDateTime.ofInstant(at, ZoneOffset.UTC)) > 0;
    }

    @Scheduled(fixedDelay = "6h", initialDelay = "10m")
    void forgetOldOutcomes() {
        try {
            int deleted = outcomes.deleteProcessedBefore(
                    LocalDateTime.now(clock).minus(REMEMBERED_FOR));
            if (deleted > 0) {
                LOG.info("forgot {} offboarding outcome(s) older than {}", deleted, REMEMBERED_FOR);
            }
        } catch (RuntimeException reapFailed) {
            // swallowed: a scheduler that stops on a throw would retire retention silently
            LOG.error("could not forget old offboarding outcomes — the rows stay", reapFailed);
        }
    }
}

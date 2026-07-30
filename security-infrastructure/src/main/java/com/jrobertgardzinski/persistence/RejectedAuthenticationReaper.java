package com.jrobertgardzinski.persistence;

import com.jrobertgardzinski.security.config.bruteforce.BruteForceConfig;
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
 * Retention for the {@code rejected_authentications} table.
 *
 * <p>{@code Source}'s javadoc states the rule this class enforces: <em>"the observed context is
 * personal data. It lives only as long as the failure records it annotates — cleaning those records
 * removes it."</em> Nothing made it true. The only deletion was per-source, run when a source
 * SUCCEEDS or gets blocked, so a source that only ever fails — every credential-stuffing scanner
 * pointed at the internet — was never cleaned at all. Its rows, an IP address and a user-agent
 * string each, stayed for good, and the table grew without a ceiling on the only axis nobody
 * watches.
 *
 * <p><strong>Why the window cannot be short.</strong> The guard counts failures inside
 * {@code failureWindowMinutes} (capped at 120 by config validation), so anything shorter than that
 * would shorten the guard's memory instead of merely forgetting history — retention must never be
 * the reason an attacker escapes a block. The floor below is a whole day past the widest window the
 * configuration will accept, and the kept period is a week, matching the other reapers here.
 */
@Singleton
@Requires(beans = DataSource.class)
class RejectedAuthenticationReaper {

    private static final Logger LOG = LoggerFactory.getLogger(RejectedAuthenticationReaper.class);

    /** How long a failed attempt is kept for forensics, once it can no longer count towards a block. */
    private static final Duration KEPT_FOR = Duration.ofDays(7);

    private final RejectedAuthenticationJdbcRepository rejections;
    private final BruteForceConfig config;
    private final Clock clock;

    RejectedAuthenticationReaper(RejectedAuthenticationJdbcRepository rejections, BruteForceConfig config,
                                 Clock clock) {
        this.rejections = rejections;
        this.config = config;
        this.clock = clock;
    }

    /**
     * Never less than the counting window plus a day — so however the window is configured, the
     * reaper cannot delete a failure that still counts towards a block.
     */
    private Duration keptFor() {
        Duration floor = Duration.ofMinutes(config.failureWindowMinutes().value()).plusDays(1);
        return KEPT_FOR.compareTo(floor) > 0 ? KEPT_FOR : floor;
    }

    @Scheduled(fixedDelay = "6h", initialDelay = "16m")
    void reap() {
        try {
            Duration kept = keptFor();
            int deleted = rejections.deleteOlderThan(LocalDateTime.now(clock).minus(kept));
            if (deleted > 0) {
                LOG.info("dropped {} rejected authentication(s) older than {}", deleted, kept);
            }
        } catch (RuntimeException reapFailed) {
            // swallowed deliberately: a scheduler that stops on a throw would retire retention silently
            LOG.error("could not drop old rejected authentications — the rows stay, the next pass retries",
                    reapFailed);
        }
    }
}

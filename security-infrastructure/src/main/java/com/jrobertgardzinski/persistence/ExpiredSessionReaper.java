package com.jrobertgardzinski.persistence;

import io.micronaut.context.annotation.Requires;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jrobertgardzinski.security.domain.vo.SessionTokensConfig;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Retention for the {@code sessions} table.
 *
 * <p>Nothing ever removed an expired session. The only deletions were logout and account removal,
 * and since no UI calls {@code POST /logout}, families were in practice never removed at all —
 * expiry did not so much as change a status. Every sign-in and every refresh therefore left a row
 * carrying an e-mail address behind, permanently: on the dev database 95% of the table was dead
 * sessions, which is a per-user login history nobody decided to keep and an obvious prize in a
 * database dump. It also made {@code listActiveSessions} lie, because it filters on
 * {@code status = 'ACTIVE'} and a row's status is never updated when its token expires.
 *
 * <p><strong>Why the cutoff is not "now".</strong> A rotated token replayed after it expires must
 * still meet reuse detection — that is how a stolen token is caught at all — so the row has to
 * outlive the token by a comfortable margin. One extra full refresh-validity window is that
 * margin: by then the token has been useless for as long as it was ever usable, and anyone
 * replaying it is replaying something no legitimate client could still hold.
 */
@Singleton
@Requires(beans = DataSource.class)
class ExpiredSessionReaper {

    private static final Logger LOG = LoggerFactory.getLogger(ExpiredSessionReaper.class);

    /**
     * The floor under the grace period, not the grace period itself.
     *
     * <p>It used to be the whole answer — a flat 24 hours, unrelated to anything. The grace has to
     * be AT LEAST one full refresh-validity window, because that is how long a rotated token could
     * still plausibly be replayed by a client that held it; deployments configure that window, and
     * a hard-coded number silently stopped matching the moment somebody set it above a day.
     */
    private static final Duration MINIMUM_GRACE = Duration.ofHours(24);

    private final SessionJdbcRepository sessions;
    private final SessionTokensConfig sessionTokens;
    private final Clock clock;

    ExpiredSessionReaper(SessionJdbcRepository sessions, SessionTokensConfig sessionTokens, Clock clock) {
        this.sessions = sessions;
        this.sessionTokens = sessionTokens;
        this.clock = clock;
    }

    /** One full refresh window past expiry, and never less than the floor above. */
    private Duration grace() {
        Duration window = Duration.ofHours(sessionTokens.refreshTokenValidityInHours().value());
        return window.compareTo(MINIMUM_GRACE) > 0 ? window : MINIMUM_GRACE;
    }

    @Scheduled(fixedDelay = "1h", initialDelay = "5m")
    void reap() {
        try {
            LocalDateTime cutoff = LocalDateTime.now(clock).minus(grace());
            int deleted = sessions.deleteExpiredBefore(cutoff);
            if (deleted > 0) {
                LOG.info("dropped {} session(s) whose refresh token expired before {}", deleted, cutoff);
            }
        } catch (RuntimeException reapFailed) {
            // swallowed deliberately: most schedulers stop invoking a task whose run threw, and
            // retiring retention after one bad database moment is how a table grows for ever
            LOG.error("could not reap expired sessions — the rows stay, the next pass retries",
                    reapFailed);
        }
    }
}

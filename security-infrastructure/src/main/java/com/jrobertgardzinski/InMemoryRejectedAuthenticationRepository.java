package com.jrobertgardzinski;

import java.time.LocalDateTime;
import java.time.Clock;
import com.jrobertgardzinski.security.domain.entity.RejectedAuthentication;
import com.jrobertgardzinski.security.domain.repository.RejectedAuthenticationRepository;
import com.jrobertgardzinski.security.domain.vo.FailuresCount;
import com.jrobertgardzinski.security.domain.vo.LockoutSubject;
import com.jrobertgardzinski.security.domain.vo.Source;
import com.jrobertgardzinski.security.domain.vo.RejectedAuthenticationDetails;
import com.jrobertgardzinski.security.domain.vo.RejectedAuthenticationId;
import io.micronaut.context.annotation.Requires;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;

import javax.sql.DataSource;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory {@link RejectedAuthenticationRepository} used when no database is configured (tests).
 * The JDBC adapter takes over once a datasource is present.
 */
@Singleton
@Requires(missingBeans = DataSource.class)
public final class InMemoryRejectedAuthenticationRepository implements RejectedAuthenticationRepository {

    private final List<RejectedAuthentication> records = new CopyOnWriteArrayList<>();
    private final AtomicLong sequence = new AtomicLong();
    private final Clock clock;
    private final com.jrobertgardzinski.security.config.bruteforce.BruteForceConfig config;

    InMemoryRejectedAuthenticationRepository(
            Clock clock,
            com.jrobertgardzinski.security.config.bruteforce.BruteForceConfig config) {
        this.clock = clock;
        this.config = config;
    }

    @Override
    public RejectedAuthentication create(RejectedAuthenticationDetails details) {
        RejectedAuthentication record = new RejectedAuthentication(
                details, new RejectedAuthenticationId(sequence.incrementAndGet()));
        records.add(record);
        return record;
    }

    /**
     * The tight count — this source against THIS account.
     *
     * <p>Matching on the whole {@link com.jrobertgardzinski.security.domain.vo.LockoutSubject} on
     * purpose: equality of "the same subject" is defined once, in the domain, so this twin cannot
     * quietly disagree with the JDBC adapter about what it means. Two implementations of one port
     * drifting on exactly that question is how a green test came to prove the wrong thing before.
     */
    @Override
    public FailuresCount countFailuresOnAccount(LockoutSubject subject, LocalDateTime since) {
        long count = records.stream()
                .map(RejectedAuthentication::details)
                .filter(details -> details.subject().equals(subject) && details.time().isAfter(since))
                .count();
        return new FailuresCount((int) count);
    }

    /** The ceiling — this source against anything, which is what catches spraying. */
    @Override
    public FailuresCount countFailuresFromSource(Source source, LocalDateTime since) {
        long count = records.stream()
                .map(RejectedAuthentication::details)
                .filter(details -> details.source().equals(source) && details.time().isAfter(since))
                .count();
        return new FailuresCount((int) count);
    }

    /**
     * Forget failures older than the window anyone still counts over.
     *
     * <p>Nothing outside the window can influence a decision — every count asks for failures
     * "since" a moment inside it — so what is left is a growing register of who failed to sign in
     * as whom, kept for no reason and paid for in memory. Anyone who can reach the sign-in endpoint
     * can add to it, which is what makes this the same finding as the OAuth flow store, not a
     * tidiness preference. Retention is TWICE the window, so a count taken a moment before the
     * sweep still sees everything it is entitled to.
     */
    @Scheduled(fixedDelay = "5m")
    void evictOutsideTheWindow() {
        LocalDateTime cutoff = LocalDateTime.now(clock)
                .minusMinutes(2L * config.failureWindowMinutes().value());
        records.removeIf(record -> record.details().time().isBefore(cutoff));
    }

    @Override
    public void removeAllFor(LockoutSubject subject) {
        records.removeIf(record -> record.details().subject().equals(subject));
    }
}

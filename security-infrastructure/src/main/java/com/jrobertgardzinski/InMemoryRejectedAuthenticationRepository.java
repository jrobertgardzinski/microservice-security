package com.jrobertgardzinski;

import com.jrobertgardzinski.security.domain.entity.RejectedAuthentication;
import com.jrobertgardzinski.security.domain.repository.RejectedAuthenticationRepository;
import com.jrobertgardzinski.security.domain.vo.FailuresCount;
import com.jrobertgardzinski.security.domain.vo.LockoutSubject;
import com.jrobertgardzinski.security.domain.vo.Source;
import com.jrobertgardzinski.security.domain.vo.RejectedAuthenticationDetails;
import com.jrobertgardzinski.security.domain.vo.RejectedAuthenticationId;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import javax.sql.DataSource;
import java.time.LocalDateTime;
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

    @Override
    public void removeAllFor(LockoutSubject subject) {
        records.removeIf(record -> record.details().subject().equals(subject));
    }
}

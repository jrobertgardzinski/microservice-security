package com.jrobertgardzinski;

import com.jrobertgardzinski.security.domain.entity.RejectedAuthentication;
import com.jrobertgardzinski.security.domain.repository.RejectedAuthenticationRepository;
import com.jrobertgardzinski.security.domain.vo.FailuresCount;
import com.jrobertgardzinski.security.domain.vo.IpAddress;
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

    @Override
    public FailuresCount countFailuresBy(IpAddress ipAddress, LocalDateTime since) {
        long count = records.stream()
                .map(RejectedAuthentication::details)
                .filter(details -> details.ipAddress().equals(ipAddress) && details.time().isAfter(since))
                .count();
        return new FailuresCount((int) count);
    }

    @Override
    public void removeAllFor(IpAddress ipAddress) {
        records.removeIf(record -> record.details().ipAddress().equals(ipAddress));
    }
}

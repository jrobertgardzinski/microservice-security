package com.jrobertgardzinski.persistence;

import com.jrobertgardzinski.security.domain.entity.RejectedAuthentication;
import com.jrobertgardzinski.security.domain.repository.RejectedAuthenticationRepository;
import com.jrobertgardzinski.security.domain.vo.FailuresCount;
import com.jrobertgardzinski.security.domain.vo.Source;
import com.jrobertgardzinski.security.domain.vo.RejectedAuthenticationDetails;
import com.jrobertgardzinski.security.domain.vo.RejectedAuthenticationId;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import javax.sql.DataSource;
import java.time.LocalDateTime;

/**
 * PostgreSQL-backed {@link RejectedAuthenticationRepository}: the failed-attempt log the brute-force
 * guard counts over a time window.
 */
@Singleton
@Requires(beans = DataSource.class)
final class JdbcRejectedAuthenticationRepository implements RejectedAuthenticationRepository {

    private final RejectedAuthenticationJdbcRepository repository;

    JdbcRejectedAuthenticationRepository(RejectedAuthenticationJdbcRepository repository) {
        this.repository = repository;
    }

    @Override
    public RejectedAuthentication create(RejectedAuthenticationDetails details) {
        RejectedAuthenticationEntity saved = repository.save(
                new RejectedAuthenticationEntity(null, details.source().ipAddress().value(),
                        details.source().userAgent(), details.time()));
        return new RejectedAuthentication(details, new RejectedAuthenticationId(saved.id()));
    }

    @Override
    public FailuresCount countFailuresBy(Source source, LocalDateTime since) {
        return new FailuresCount((int) repository.countByIpAddressAndOccurredAtAfter(source.ipAddress().value(), since));
    }

    @Override
    public void removeAllFor(Source source) {
        repository.deleteByIpAddress(source.ipAddress().value());
    }
}

package com.jrobertgardzinski.persistence;

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

/**
 * PostgreSQL-backed {@link RejectedAuthenticationRepository}: the failed-attempt log the brute-force
 * guard counts over a time window, on two scales — per (source, account) and per source.
 *
 * <p>This is the ONLY place that knows the account is stored as a fingerprint rather than as an
 * address ({@link AccountFingerprint} explains why). Everything above speaks in
 * {@link com.jrobertgardzinski.security.domain.vo.AttemptedAccount}; the secret that turns one into
 * the other is infrastructure's business and never climbs out of this layer.
 */
@Singleton
@Requires(beans = DataSource.class)
final class JdbcRejectedAuthenticationRepository implements RejectedAuthenticationRepository {

    private final RejectedAuthenticationJdbcRepository repository;
    private final AccountFingerprint fingerprint;

    JdbcRejectedAuthenticationRepository(RejectedAuthenticationJdbcRepository repository,
                                         AccountFingerprint fingerprint) {
        this.repository = repository;
        this.fingerprint = fingerprint;
    }

    @Override
    public RejectedAuthentication create(RejectedAuthenticationDetails details) {
        RejectedAuthenticationEntity saved = repository.save(
                new RejectedAuthenticationEntity(null, details.source().ipAddress().value(),
                        details.source().userAgent(), fingerprint.of(details.account()), details.time()));
        return new RejectedAuthentication(details, new RejectedAuthenticationId(saved.id()));
    }

    @Override
    public FailuresCount countFailuresOnAccount(LockoutSubject subject, LocalDateTime since) {
        return new FailuresCount((int) repository.countByIpAddressAndAccountFingerprintAndOccurredAtAfter(
                subject.source().ipAddress().value(), fingerprint.of(subject.account()), since));
    }

    @Override
    public FailuresCount countFailuresFromSource(Source source, LocalDateTime since) {
        return new FailuresCount(
                (int) repository.countByIpAddressAndOccurredAtAfter(source.ipAddress().value(), since));
    }

    @Override
    public void removeAllFor(LockoutSubject subject) {
        repository.deleteByIpAddressAndAccountFingerprint(
                subject.source().ipAddress().value(), fingerprint.of(subject.account()));
    }
}

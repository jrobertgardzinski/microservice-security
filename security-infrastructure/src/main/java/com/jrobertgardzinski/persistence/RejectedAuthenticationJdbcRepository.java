package com.jrobertgardzinski.persistence;

import io.micronaut.context.annotation.Requires;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

import javax.sql.DataSource;
import java.time.LocalDateTime;

@JdbcRepository(dialect = Dialect.POSTGRES)
@Requires(beans = DataSource.class)
interface RejectedAuthenticationJdbcRepository extends CrudRepository<RejectedAuthenticationEntity, Long> {

    /** The ceiling: this address against anything at all — the shape of spraying. */
    long countByIpAddressAndOccurredAtAfter(String ipAddress, LocalDateTime since);

    /** The tight count: this address against ONE account — the shape of guessing a password. */
    long countByIpAddressAndAccountFingerprintAndOccurredAtAfter(
            String ipAddress, String accountFingerprint, LocalDateTime since);

    /** Forget one pair's failures; the rest of the address's record is other people's business. */
    void deleteByIpAddressAndAccountFingerprint(String ipAddress, String accountFingerprint);

    /**
     * Retention: a failure older than the cutoff is deleted.
     *
     * <p>The only deletion before this was {@code deleteByIpAddress}, run when a source SUCCEEDS or
     * gets blocked. A source that only ever fails — which is every scanner on the internet — was
     * therefore never cleaned at all: its rows, each an IP address plus a user-agent string, stayed
     * for ever. {@code Source}'s own javadoc promises the opposite ("the observed context is personal
     * data; it lives only as long as the failure records it annotates"), and there was nothing to
     * make that true.
     */
    @Query("DELETE FROM rejected_authentications WHERE occurred_at < :cutoff")
    int deleteOlderThan(LocalDateTime cutoff);
}

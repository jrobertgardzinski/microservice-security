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
interface ProcessedOutcomeJdbcRepository extends CrudRepository<ProcessedOutcomeEntity, String> {

    /**
     * Claims this outcome id, and says whether THIS caller claimed it.
     *
     * <p>One conditional INSERT rather than "exists? then insert": two partitions of the same
     * consumer group, or a retry racing a re-announcement, must not both act on the same outcome.
     * Under READ COMMITTED the loser blocks on the primary key and then inserts zero rows.
     */
    @Query("INSERT INTO processed_offboarding_outcomes (id, outcome_type, processed_at)"
            + " VALUES (:id, :outcomeType, :processedAt) ON CONFLICT (id) DO NOTHING")
    int claim(String id, String outcomeType, LocalDateTime processedAt);

    /** Retention: an outcome that can still be re-announced is young; the rest is history. */
    @Query("DELETE FROM processed_offboarding_outcomes WHERE processed_at < :cutoff")
    int deleteProcessedBefore(LocalDateTime cutoff);
}

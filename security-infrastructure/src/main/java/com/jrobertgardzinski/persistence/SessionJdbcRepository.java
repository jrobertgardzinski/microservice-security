package com.jrobertgardzinski.persistence;

import io.micronaut.context.annotation.Requires;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

import javax.sql.DataSource;
import java.util.Optional;
import java.util.UUID;

@JdbcRepository(dialect = Dialect.POSTGRES)
@Requires(beans = DataSource.class)
interface SessionJdbcRepository extends CrudRepository<SessionEntity, String> {

    Optional<SessionEntity> findByAccessTokenHashAndStatus(String accessTokenHash, String status);

    /**
     * The sessions to SHOW a user: active by status and still valid by the clock.
     *
     * <p>Both halves are needed and only the first used to be applied. {@code ExpiredSessionReaper}
     * keeps expired rows on purpose — for the longer of the refresh window and 24 hours, plus up to
     * its own interval — so that a replayed just-expired token still meets reuse detection. Listing
     * on the status alone therefore showed a device that had stopped working a day earlier as a
     * place the user is currently signed in, and nothing could clear it: revoking removes rows of a
     * lineage, and this row was already dead. Its own javadoc said "status is not the truth, the
     * expiry column is", in the past tense, about this very listing.
     */
    java.util.List<SessionEntity> findByEmailAndStatusAndRefreshTokenExpirationAfter(
            String email, String status, java.time.LocalDateTime now);

    @Query("UPDATE sessions SET status = :status WHERE refresh_token_hash = :refreshTokenHash")
    void updateStatus(String refreshTokenHash, String status);

    /**
     * The rotation, as ONE conditional statement, returning how many rows it changed.
     *
     * <p>{@code AND status = 'ACTIVE'} is not a formality — it is the entire concurrency control.
     * Under READ COMMITTED the second transaction blocks on this row's lock, and when the first
     * commits it re-evaluates the predicate against the new version (EvalPlanQual), finds ROTATED
     * and updates zero rows. Exactly one caller can therefore win a race, which is what makes a
     * replay distinguishable from a legitimate refresh at all.
     */
    @Query("UPDATE sessions SET status = 'ROTATED' WHERE refresh_token_hash = :refreshTokenHash"
            + " AND status = 'ACTIVE'")
    int rotateIfActive(String refreshTokenHash);

    void deleteByFamilyId(UUID familyId);

    /**
     * Retention for the session table, which nothing used to provide.
     *
     * <p>The only deletions were logout and account removal — and since no UI ever calls
     * {@code POST /logout} (P12 W1), families were in practice never removed at all. Expiry did
     * not even change a status, so every sign-in and every refresh left a row carrying an e-mail
     * address, for ever: a database dump handed over a per-user login history nobody meant to
     * keep, and {@code listActiveSessions} filtered on {@code status = 'ACTIVE'} while long-dead
     * rows still claimed to be active — status is not the truth, the expiry column is.
     *
     * @param cutoff delete rows whose refresh token expired before this. Callers pass a cutoff a
     *               full refresh-validity window in the past rather than "now": a replay of a
     *               just-expired rotated token must still meet reuse detection before its row
     *               disappears, or theft would become undetectable simply by waiting.
     */
    @Query("DELETE FROM sessions WHERE refresh_token_expiration < :cutoff")
    int deleteExpiredBefore(java.time.LocalDateTime cutoff);

    void deleteByEmail(String email);
}

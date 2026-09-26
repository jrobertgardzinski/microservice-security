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
interface UserJdbcRepository extends CrudRepository<UserEntity, UUID> {

    @Query("UPDATE users SET pending_deletion = :pending WHERE email = :email")
    void setPendingDeletion(String email, boolean pending);

    boolean existsByEmailAndPendingDeletionTrue(String email);


    Optional<UserEntity> findByEmail(String email);
    java.util.List<UserEntity> findByIdIn(java.util.Collection<UUID> ids);

    boolean existsByNormalizedEmail(String normalizedEmail);

    java.util.Optional<UserEntity> findByNormalizedEmail(String normalizedEmail);

    @Query("UPDATE users SET password_hash = :passwordHash WHERE email = :email")
    void updatePassword(String email, String passwordHash);

    @Query("UPDATE users SET roles = :roles WHERE email = :email")
    void setRoles(String email, String roles);

    @Query("UPDATE users SET email = :newEmail, normalized_email = :normalizedEmail WHERE email = :currentEmail")
    void updateEmail(String currentEmail, String newEmail, String normalizedEmail);

    void deleteByEmail(String email);

    /**
     * Accounts holding ADMIN. Roles are stored as a sorted comma-separated list in one column, so
     * the match is on the list's ELEMENT: {@code LIKE '%ADMIN%'} would also count a role named
     * SUPERADMIN the day somebody adds one.
     */
    @Query("SELECT COUNT(*) FROM users WHERE 'ADMIN' = ANY (string_to_array(roles, ','))")
    int countAdmins();

    /**
     * Accounts opened before the cutoff whose address was NEVER verified — the ones the retention
     * sweep deletes.
     *
     * <p>"Never verified" is the join's business: a row in {@code email_verifications} with
     * {@code verified = true} is the only thing that makes an address confirmed, and an account
     * with NO row there (its pending one already swept, or a seeded account that never had one) is
     * unverified too. A LEFT JOIN says both at once.
     *
     * <p>An account already on its way out ({@code pending_deletion}) is left to the saga that is
     * deleting it — two deletions of one account racing each other is nobody's idea of retention.
     */
    @Query("SELECT u.* FROM users u LEFT JOIN email_verifications v ON v.email = u.email"
            + " WHERE u.created_at < :cutoff AND u.pending_deletion = FALSE"
            + " AND COALESCE(v.verified, FALSE) = FALSE")
    java.util.List<UserEntity> findUnverifiedOpenedBefore(java.time.LocalDateTime cutoff);
}

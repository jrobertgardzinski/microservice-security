package com.jrobertgardzinski.security.domain.repository;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.email.domain.NormalizedEmail;
import com.jrobertgardzinski.password.domain.HashedPassword;
import com.jrobertgardzinski.security.domain.entity.User;
import com.jrobertgardzinski.security.domain.vo.Role;

import java.util.Optional;
import java.util.Set;

public interface UserRepository {

    Optional<User> findBy(Email email);

    /** Replace a user's whole role set (USER is always kept); a no-op if the user is absent. */
    void setRoles(Email email, Set<Role> roles);

    /** Replace an existing user's password hash (e.g. after a password reset); a no-op if absent. */
    void updatePassword(Email email, HashedPassword passwordHash);

    /** Move an existing user to a new email (and its normalized form); a no-op if absent. */
    void updateEmail(Email currentEmail, Email newEmail);

    /** Delete a user by email (close account); a no-op if absent. */
    void deleteByEmail(Email email);

    /** Lock the account while its deletion saga runs: the user stays but cannot sign in. */
    void markPendingDeletion(Email email);

    /** Roll the deletion lock back (saga compensation): the account works again. */
    void clearPendingDeletion(Email email);

    boolean isPendingDeletion(Email email);

    /**
     * Whether a user already exists under the given normalized email — the identity
     * used for registration deduplication, so provider aliases (e.g. Gmail dots or
     * {@code +tags}) of the same address count as taken.
     */
    boolean existsBy(NormalizedEmail normalizedEmail);

    /**
     * Persist a NEW user. One semantics for every adapter, with or without a database: the
     * (normalized) address is unique, and an attempt to save one that is already taken throws
     * {@link EmailAlreadyTakenException} — it never overwrites the account sitting there.
     *
     * <p>Spelled out because the two adapters used to disagree: the JDBC one translated the unique
     * violation into the exception, the in-memory one (which serves whenever no datasource is
     * configured) silently replaced the existing row. The same registration losing a race therefore
     * ended as "email already taken" with a database and as a stolen account without one.
     */
    User save(User user);
}

package com.jrobertgardzinski.security.domain.repository;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.email.domain.NormalizedEmail;
import com.jrobertgardzinski.password.domain.HashedPassword;
import com.jrobertgardzinski.security.domain.entity.User;
import com.jrobertgardzinski.security.domain.vo.Role;

import java.util.Optional;
import java.util.Set;

public interface UserRepository {

    /**
     * The account this address belongs to — matched by its NORMALIZED form, which is the same rule
     * {@link #save} refuses duplicates by.
     *
     * <p>The two have to agree, and for a long time they did not: registration refused a second
     * {@code alice@corp.com} once {@code Alice@corp.com} existed, while this lookup answered empty
     * for it — so the person who capitalised their own name on the day they signed up was told
     * "wrong e-mail or password" for ever after, and could not register again either.
     */
    Optional<User> findBy(Email email);

    /** Replace a user's whole role set (USER is always kept); a no-op if the user is absent. */
    void setRoles(Email email, Set<Role> roles);

    /**
     * How many accounts hold ADMIN right now.
     *
     * <p>Asked before a role change takes ADMIN away: the one question the caller cannot answer for
     * themselves. Granting and revoking roles is itself an admin action, so an administrator who
     * drops their own grant while being the last one leaves a system in which nobody can grant it
     * back — the only way out being the deployment's {@code security.bootstrap-admins}, if it names
     * anybody at all.
     *
     * <p>Bootstrap admins are deliberately NOT counted here: they are admins by CONFIGURATION, not
     * by a row, and whether the deployment declares any is the use case's business to combine with
     * this number.
     */
    int countAdmins();

    /** Replace an existing user's password hash (e.g. after a password reset); a no-op if absent. */
    void updatePassword(Email email, HashedPassword passwordHash);

    /**
     * Move an existing user to a new email (and its normalized form); a no-op if absent.
     *
     * <p>The new address is UNIQUE here exactly as it is in {@link #save}, and an attempt to move
     * onto one that is already taken throws {@link EmailAlreadyTakenException} — it never
     * overwrites the account sitting there.
     *
     * <p>Said out loud because the two adapters disagreed, and a change token lives for a day: the
     * address is free when the change is REQUESTED and can be registered by somebody else before
     * the link is followed. With a database the unique index refused and the raw violation escaped
     * as a 500 (the same link then answering 500 until it expired); without one, the in-memory
     * adapter quietly replaced the other person's account with this one. Same call, same input,
     * one answer.
     */
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

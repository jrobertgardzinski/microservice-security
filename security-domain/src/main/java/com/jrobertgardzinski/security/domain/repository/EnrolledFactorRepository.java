package com.jrobertgardzinski.security.domain.repository;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.entity.EnrolledFactor;
import com.jrobertgardzinski.security.domain.vo.FactorType;

import java.util.List;

/**
 * The factors each user has enrolled — the per-user half of the MFA configuration (the deployment
 * half is which adapters are enabled). Ordered by the chain position the user chose.
 */
public interface EnrolledFactorRepository {

    /** A user's enrolled factors, ordered by their chain position. */
    List<EnrolledFactor> findByUser(Email userEmail);

    /** Add or replace (same user + type) an enrolled factor. */
    void enrol(EnrolledFactor factor);

    /** Remove one factor; a no-op if it was not enrolled. */
    void remove(Email userEmail, FactorType type);

    /** Remove every factor a user has — an admin reset when someone is locked out of all of them. */
    void removeAll(Email userEmail);
}

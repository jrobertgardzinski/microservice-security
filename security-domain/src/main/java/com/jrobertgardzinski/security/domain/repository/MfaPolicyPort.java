package com.jrobertgardzinski.security.domain.repository;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.vo.FactorType;

import java.util.List;

/**
 * Returns the ordered list of identity factors required to authenticate
 * a given user. The first factor is always {@link FactorType#CREDENTIALS}.
 *
 * MVP implementation may return the same list for everyone; future
 * implementations can read per-user configuration.
 */
public interface MfaPolicyPort {

    List<FactorType> requiredFactors(Email email);
}

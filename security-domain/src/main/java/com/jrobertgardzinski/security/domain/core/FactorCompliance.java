package com.jrobertgardzinski.security.domain.core;

import com.jrobertgardzinski.email.domain.Email;

import java.util.Set;

/** How a person's factors stand against the floor their roles demand. */
public interface FactorCompliance {

    boolean isCompliant(Email email, Set<Role> roles);

    int requiredFactors(Set<Role> roles);

    int effectiveFactorCount(Email email);
}

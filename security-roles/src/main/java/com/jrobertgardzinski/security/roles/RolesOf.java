package com.jrobertgardzinski.security.roles;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.vo.Role;

import java.util.Set;

/** The roles the store has granted this identity; an unknown identity has none. */
@FunctionalInterface
public interface RolesOf {
    Set<Role> of(Email email);
}

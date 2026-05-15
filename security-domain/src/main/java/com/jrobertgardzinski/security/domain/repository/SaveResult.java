package com.jrobertgardzinski.security.domain.repository;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.entity.User;

/**
 * Result of {@link UserRepository#save}. Adapters translate their native
 * unique-constraint violation into {@link AlreadyExists}; any other failure
 * (DB down, timeout, etc.) propagates as an exception.
 */
public sealed interface SaveResult {

    record Saved(User user) implements SaveResult {}

    record AlreadyExists(Email email) implements SaveResult {}
}

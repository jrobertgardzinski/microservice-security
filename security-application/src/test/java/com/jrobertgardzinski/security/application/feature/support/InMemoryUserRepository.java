package com.jrobertgardzinski.security.application.feature.support;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.email.domain.NormalizedEmail;
import com.jrobertgardzinski.security.domain.entity.User;
import com.jrobertgardzinski.security.domain.repository.UserRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Keyed by email value (string) so lookups don't depend on Email's identity. */
public final class InMemoryUserRepository implements UserRepository {

    private final Map<String, User> byEmail = new HashMap<>();
    private final Map<String, User> byNormalizedEmail = new HashMap<>();

    @Override
    public Optional<User> findBy(Email email) {
        return Optional.ofNullable(byEmail.get(email.value()));
    }

    @Override
    public boolean existsBy(NormalizedEmail normalizedEmail) {
        return byNormalizedEmail.containsKey(normalizedEmail.value());
    }

    @Override
    public User save(User user) {
        byEmail.put(user.email().value(), user);
        byNormalizedEmail.put(user.normalizedEmail().value(), user);
        return user;
    }
}

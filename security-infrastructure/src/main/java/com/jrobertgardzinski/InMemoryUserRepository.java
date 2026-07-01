package com.jrobertgardzinski;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.email.domain.NormalizedEmail;
import com.jrobertgardzinski.security.domain.entity.User;
import com.jrobertgardzinski.security.domain.repository.UserRepository;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import javax.sql.DataSource;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link UserRepository} used when no database is configured (tests). The JDBC adapter
 * takes over once a datasource is present. Keyed by the string value of the email so lookups don't
 * depend on {@code Email} identity; a second index on the normalized email backs registration
 * deduplication, so provider aliases (Gmail dots / {@code +tags}) of the same address count as taken.
 */
@Singleton
@Requires(missingBeans = DataSource.class)
public final class InMemoryUserRepository implements UserRepository {

    private final Map<String, User> byEmail = new ConcurrentHashMap<>();
    private final Map<String, User> byNormalizedEmail = new ConcurrentHashMap<>();

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

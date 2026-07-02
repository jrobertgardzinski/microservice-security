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

    private final java.util.Set<String> pendingDeletion = java.util.concurrent.ConcurrentHashMap.newKeySet();
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

    @Override
    public void updatePassword(com.jrobertgardzinski.email.domain.Email email,
                               com.jrobertgardzinski.password.domain.HashedPassword passwordHash) {
        User existing = byEmail.get(email.value());
        if (existing != null) {
            User updated = new User(existing.id(), existing.email(), passwordHash, existing.normalizedEmail());
            byEmail.put(email.value(), updated);
            byNormalizedEmail.put(existing.normalizedEmail().value(), updated);
        }
    }

    @Override
    public void updateEmail(Email currentEmail, Email newEmail) {
        User existing = byEmail.remove(currentEmail.value());
        if (existing != null) {
            byNormalizedEmail.remove(existing.normalizedEmail().value());
            User moved = new User(existing.id(), newEmail, existing.passwordHash(), NormalizedEmail.of(newEmail));
            byEmail.put(newEmail.value(), moved);
            byNormalizedEmail.put(moved.normalizedEmail().value(), moved);
        }
    }

    @Override
    public void deleteByEmail(Email email) {
        User removed = byEmail.remove(email.value());
        if (removed != null) {
            byNormalizedEmail.remove(removed.normalizedEmail().value());
        }
        pendingDeletion.remove(email.value());
    }

    @Override
    public void markPendingDeletion(Email email) {
        pendingDeletion.add(email.value());
    }

    @Override
    public void clearPendingDeletion(Email email) {
        pendingDeletion.remove(email.value());
    }

    @Override
    public boolean isPendingDeletion(Email email) {
        return pendingDeletion.contains(email.value());
    }
}

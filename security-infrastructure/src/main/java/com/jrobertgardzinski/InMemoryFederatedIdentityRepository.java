package com.jrobertgardzinski;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.repository.FederatedIdentityRepository;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import javax.sql.DataSource;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link FederatedIdentityRepository} used when no database is configured (tests). The
 * JDBC adapter takes over once a datasource is present.
 */
@Singleton
@Requires(missingBeans = DataSource.class)
public final class InMemoryFederatedIdentityRepository implements FederatedIdentityRepository {

    private final Map<String, String> userBySubject = new ConcurrentHashMap<>();

    @Override
    public Optional<Email> findUserBy(String provider, String subject) {
        return Optional.ofNullable(userBySubject.get(provider + "|" + subject)).map(Email::of);
    }

    @Override
    public void link(String provider, String subject, Email userEmail) {
        userBySubject.put(provider + "|" + subject, userEmail.value());
    }

    @Override
    public void unlinkAll(Email userEmail) {
        userBySubject.values().removeIf(userEmail.value()::equals);
    }
}

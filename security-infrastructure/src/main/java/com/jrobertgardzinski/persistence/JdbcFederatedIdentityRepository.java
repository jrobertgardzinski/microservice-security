package com.jrobertgardzinski.persistence;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.repository.FederatedIdentityRepository;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * PostgreSQL-backed {@link FederatedIdentityRepository}. Re-linking the same {@code (provider,
 * subject)} replaces the row (upsert) — the repository contract's "one identity opens one account".
 */
@Singleton
@Requires(beans = DataSource.class)
final class JdbcFederatedIdentityRepository implements FederatedIdentityRepository {

    private final FederatedIdentityJdbcRepository repository;
    private final Clock clock;

    JdbcFederatedIdentityRepository(FederatedIdentityJdbcRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public Optional<Email> findUserBy(String provider, String subject) {
        return repository.findById(FederatedIdentityEntity.keyOf(provider, subject))
                .map(entity -> Email.of(entity.userEmail()));
    }

    @Override
    public void link(String provider, String subject, Email userEmail) {
        String key = FederatedIdentityEntity.keyOf(provider, subject);
        repository.deleteById(key); // upsert
        repository.save(new FederatedIdentityEntity(key, provider, subject,
                userEmail.value(), LocalDateTime.now(clock)));
    }
}

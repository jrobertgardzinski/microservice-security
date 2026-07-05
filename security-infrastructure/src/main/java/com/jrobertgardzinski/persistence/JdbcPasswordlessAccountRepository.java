package com.jrobertgardzinski.persistence;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.repository.PasswordlessAccountRepository;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import javax.sql.DataSource;

/** PostgreSQL-backed {@link PasswordlessAccountRepository}: presence of a row = passwordless. */
@Singleton
@Requires(beans = DataSource.class)
final class JdbcPasswordlessAccountRepository implements PasswordlessAccountRepository {

    private final PasswordlessAccountJdbcRepository repository;

    JdbcPasswordlessAccountRepository(PasswordlessAccountJdbcRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean isPasswordless(Email email) {
        return repository.existsById(email.value());
    }

    @Override
    public void setPasswordless(Email email, boolean value) {
        if (value) {
            if (!repository.existsById(email.value())) {
                repository.save(new PasswordlessAccountEntity(email.value()));
            }
        } else {
            repository.deleteById(email.value());
        }
    }
}

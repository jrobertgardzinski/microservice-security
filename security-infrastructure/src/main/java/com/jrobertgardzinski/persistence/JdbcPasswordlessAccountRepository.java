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

    /** The row IS the address (it is the primary key), so a move is a delete plus an insert. */
    @Override
    public void reassign(Email fromEmail, Email toEmail) {
        if (repository.existsById(fromEmail.value())) {
            repository.deleteById(fromEmail.value());
            if (!repository.existsById(toEmail.value())) {
                repository.save(new PasswordlessAccountEntity(toEmail.value()));
            }
        }
    }

    @Override
    public void purge(Email email) {
        repository.deleteById(email.value());
    }
}

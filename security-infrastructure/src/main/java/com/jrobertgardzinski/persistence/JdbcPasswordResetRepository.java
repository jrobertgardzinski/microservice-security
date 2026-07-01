package com.jrobertgardzinski.persistence;

import com.jrobertgardzinski.TokenHashing;
import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.repository.PasswordResetRepository;
import com.jrobertgardzinski.security.domain.vo.token.PasswordResetToken;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import javax.sql.DataSource;
import java.util.Optional;

/**
 * PostgreSQL-backed {@link PasswordResetRepository}. Stores the pending token as a SHA-256 hash (see
 * {@link TokenHashing}); consuming it matches on that hash, deletes the row (single-use) and returns
 * the address. Raw tokens are never stored.
 */
@Singleton
@Requires(beans = DataSource.class)
final class JdbcPasswordResetRepository implements PasswordResetRepository {

    private final PasswordResetJdbcRepository repository;

    JdbcPasswordResetRepository(PasswordResetJdbcRepository repository) {
        this.repository = repository;
    }

    @Override
    public void startReset(Email email, PasswordResetToken token) {
        repository.deleteById(email.value());   // re-requesting reissues a fresh token
        repository.save(new PasswordResetEntity(email.value(), TokenHashing.hash(token)));
    }

    @Override
    public Optional<Email> consumeReset(PasswordResetToken token) {
        return repository.findByTokenHash(TokenHashing.hash(token)).map(entity -> {
            repository.deleteById(entity.email());
            return Email.of(entity.email());
        });
    }
}

package com.jrobertgardzinski.persistence;

import com.jrobertgardzinski.TokenHashing;
import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.repository.EmailChangeRepository;
import com.jrobertgardzinski.security.domain.vo.EmailChange;
import com.jrobertgardzinski.security.domain.vo.token.VerificationToken;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import javax.sql.DataSource;
import java.util.Optional;

/**
 * PostgreSQL-backed {@link EmailChangeRepository}. Keyed by the SHA-256 hash of the pending token
 * (see {@link TokenHashing}); confirming matches on that hash, deletes the row (single-use) and
 * returns the change. Raw tokens are never stored.
 */
@Singleton
@Requires(beans = DataSource.class)
final class JdbcEmailChangeRepository implements EmailChangeRepository {

    private final EmailChangeJdbcRepository repository;

    JdbcEmailChangeRepository(EmailChangeJdbcRepository repository) {
        this.repository = repository;
    }

    @Override
    public void startChange(EmailChange change, VerificationToken token) {
        repository.save(new EmailChangeEntity(
                TokenHashing.hash(token), change.currentEmail().value(), change.newEmail().value()));
    }

    @Override
    public Optional<EmailChange> confirmChange(VerificationToken token) {
        return repository.findById(TokenHashing.hash(token)).map(entity -> {
            repository.deleteById(entity.tokenHash());
            return new EmailChange(Email.of(entity.currentEmail()), Email.of(entity.newEmail()));
        });
    }
}

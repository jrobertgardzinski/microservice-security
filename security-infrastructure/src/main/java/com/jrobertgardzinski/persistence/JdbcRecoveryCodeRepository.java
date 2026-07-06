package com.jrobertgardzinski.persistence;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.repository.RecoveryCodeRepository;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import javax.sql.DataSource;
import java.util.List;

/**
 * PostgreSQL-backed {@link RecoveryCodeRepository}. Consumption is a single conditional UPDATE,
 * so a code can never be spent twice, whatever the concurrency. Active only when a datasource is
 * present.
 */
@Singleton
@Requires(beans = DataSource.class)
final class JdbcRecoveryCodeRepository implements RecoveryCodeRepository {

    private final RecoveryCodeJdbcRepository repository;

    JdbcRecoveryCodeRepository(RecoveryCodeJdbcRepository repository) {
        this.repository = repository;
    }

    @Override
    public void replaceAll(Email userEmail, List<String> codeHashes) {
        repository.deleteByUserEmail(userEmail.value());
        repository.saveAll(codeHashes.stream()
                .map(hash -> new RecoveryCodeEntity(
                        RecoveryCodeEntity.keyOf(userEmail.value(), hash),
                        userEmail.value(), hash, false))
                .toList());
    }

    @Override
    public boolean consume(Email userEmail, String codeHash) {
        return repository.spend(RecoveryCodeEntity.keyOf(userEmail.value(), codeHash)) == 1;
    }

    @Override
    public int unusedCount(Email userEmail) {
        return (int) repository.countByUserEmailAndUsedFalse(userEmail.value());
    }
}

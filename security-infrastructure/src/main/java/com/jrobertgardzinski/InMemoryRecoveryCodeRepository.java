package com.jrobertgardzinski;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.repository.RecoveryCodeRepository;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link RecoveryCodeRepository} used when no database is configured (tests). The JDBC
 * adapter takes over once a datasource is present. Keyed by the string value of the email.
 */
@Singleton
@Requires(missingBeans = DataSource.class)
public final class InMemoryRecoveryCodeRepository implements RecoveryCodeRepository {

    /** email -> (hash -> spent?) */
    private final Map<String, Map<String, Boolean>> byUser = new ConcurrentHashMap<>();

    @Override
    public void replaceAll(Email userEmail, List<String> codeHashes) {
        Map<String, Boolean> fresh = new ConcurrentHashMap<>();
        codeHashes.forEach(hash -> fresh.put(hash, Boolean.FALSE));
        byUser.put(userEmail.value(), fresh);
    }

    @Override
    public boolean consume(Email userEmail, String codeHash) {
        Map<String, Boolean> codes = byUser.get(userEmail.value());
        // the atomic unused->used flip is the single-use guarantee, same as the JDBC UPDATE
        return codes != null && codes.replace(codeHash, Boolean.FALSE, Boolean.TRUE);
    }

    @Override
    public int unusedCount(Email userEmail) {
        return (int) byUser.getOrDefault(userEmail.value(), Map.of())
                .values().stream().filter(spent -> !spent).count();
    }

    @Override
    public void removeAll(Email userEmail) {
        byUser.remove(userEmail.value());
    }
}

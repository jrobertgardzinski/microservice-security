package com.jrobertgardzinski;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.repository.PasswordResetRepository;
import com.jrobertgardzinski.security.domain.vo.token.PasswordResetToken;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import javax.sql.DataSource;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link PasswordResetRepository} used when no database is configured (tests). Keyed by
 * e-mail, holding the SHA-256 hash of the pending token; consuming it removes the entry, so the
 * token is single-use. Raw tokens are never stored.
 */
@Singleton
@Requires(missingBeans = DataSource.class)
final class InMemoryPasswordResetRepository implements PasswordResetRepository {

    private final Map<String, String> tokenHashByEmail = new ConcurrentHashMap<>();

    @Override
    public void startReset(Email email, PasswordResetToken token) {
        tokenHashByEmail.put(email.value(), TokenHashing.hash(token));
    }

    @Override
    public Optional<Email> consumeReset(PasswordResetToken token) {
        String hash = TokenHashing.hash(token);
        for (Map.Entry<String, String> entry : tokenHashByEmail.entrySet()) {
            if (hash.equals(entry.getValue())) {
                tokenHashByEmail.remove(entry.getKey());
                return Optional.of(Email.of(entry.getKey()));
            }
        }
        return Optional.empty();
    }
}

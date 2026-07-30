package com.jrobertgardzinski;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.repository.PasswordResetRepository;
import com.jrobertgardzinski.security.domain.vo.token.PasswordResetToken;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.LocalDateTime;
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

    private record Row(String tokenHash, LocalDateTime requestedAt) {}

    private final Map<String, Row> byEmail = new ConcurrentHashMap<>();
    private final Clock clock;

    InMemoryPasswordResetRepository(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void startReset(Email email, PasswordResetToken token) {
        byEmail.put(email.value(), new Row(TokenHashing.hash(token), LocalDateTime.now(clock)));
    }

    @Override
    public Optional<PendingReset> consumeReset(PasswordResetToken token) {
        String hash = TokenHashing.hash(token);
        for (Map.Entry<String, Row> entry : byEmail.entrySet()) {
            if (hash.equals(entry.getValue().tokenHash())) {
                byEmail.remove(entry.getKey());
                return Optional.of(new PendingReset(Email.of(entry.getKey()), entry.getValue().requestedAt()));
            }
        }
        return Optional.empty();
    }

    @Override
    public void purge(Email email) {
        byEmail.remove(email.value());
    }
}

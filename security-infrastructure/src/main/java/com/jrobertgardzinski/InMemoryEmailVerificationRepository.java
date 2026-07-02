package com.jrobertgardzinski;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.repository.EmailVerificationRepository;
import com.jrobertgardzinski.security.domain.vo.token.VerificationToken;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import javax.sql.DataSource;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link EmailVerificationRepository} used when no database is configured (tests). Keyed
 * by e-mail, each row holds the SHA-256 hash of the pending token (never the raw token) and whether
 * the address is verified.
 */
@Singleton
@Requires(missingBeans = DataSource.class)
final class InMemoryEmailVerificationRepository implements EmailVerificationRepository {

    private record Row(String pendingTokenHash, boolean verified) {}

    private final Map<String, Row> byEmail = new ConcurrentHashMap<>();

    @Override
    public void startVerification(Email email, VerificationToken token) {
        byEmail.put(email.value(), new Row(TokenHashing.hash(token), false));
    }

    @Override
    public Optional<Email> completeVerification(VerificationToken token) {
        String hash = TokenHashing.hash(token);
        return byEmail.entrySet().stream()
                .filter(e -> hash.equals(e.getValue().pendingTokenHash()))
                .findFirst()
                .map(e -> {
                    byEmail.put(e.getKey(), new Row(null, true));
                    return Email.of(e.getKey());
                });
    }

    @Override
    public void markVerified(Email email) {
        byEmail.put(email.value(), new Row(null, true));
    }

    @Override
    public boolean isVerified(Email email) {
        Row row = byEmail.get(email.value());
        return row != null && row.verified();
    }
}

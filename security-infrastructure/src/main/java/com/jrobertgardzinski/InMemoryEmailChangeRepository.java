package com.jrobertgardzinski;

import com.jrobertgardzinski.security.domain.repository.EmailChangeRepository;
import com.jrobertgardzinski.security.domain.vo.EmailChange;
import com.jrobertgardzinski.security.domain.vo.token.VerificationToken;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import javax.sql.DataSource;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link EmailChangeRepository} used when no database is configured (tests). Keyed by the
 * SHA-256 hash of the pending token; confirming removes the entry, so the token is single-use.
 */
@Singleton
@Requires(missingBeans = DataSource.class)
final class InMemoryEmailChangeRepository implements EmailChangeRepository {

    private final Map<String, EmailChange> byTokenHash = new ConcurrentHashMap<>();

    @Override
    public void startChange(EmailChange change, VerificationToken token) {
        byTokenHash.put(TokenHashing.hash(token), change);
    }

    @Override
    public Optional<EmailChange> confirmChange(VerificationToken token) {
        return Optional.ofNullable(byTokenHash.remove(TokenHashing.hash(token)));
    }
}

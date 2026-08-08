package com.jrobertgardzinski;

import java.time.LocalDateTime;
import java.time.Clock;
import com.jrobertgardzinski.security.domain.repository.EmailChangeRepository;
import com.jrobertgardzinski.security.domain.vo.EmailChange;
import com.jrobertgardzinski.security.domain.vo.token.VerificationToken;
import io.micronaut.context.annotation.Requires;
import io.micronaut.scheduling.annotation.Scheduled;
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

    private final Map<String, EmailChangeRepository.PendingEmailChange> byTokenHash = new ConcurrentHashMap<>();
    private final Clock clock;

    InMemoryEmailChangeRepository(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void startChange(EmailChange change, VerificationToken token) {
        byTokenHash.put(TokenHashing.hash(token),
                new EmailChangeRepository.PendingEmailChange(change, LocalDateTime.now(clock)));
    }

    @Override
    public Optional<EmailChangeRepository.PendingEmailChange> confirmChange(VerificationToken token) {
        return Optional.ofNullable(byTokenHash.remove(TokenHashing.hash(token)));
    }

    /**
     * Drop tickets nobody confirmed. The twin with a datasource has a reaper doing exactly this;
     * without one here, every abandoned change request stays for the life of the process, and
     * starting one costs a request. RETENTION is deliberately generous — far longer than the window
     * the use case judges by — so that expiry is decided in ONE place and this only sweeps up what
     * could not possibly matter any more.
     */
    @Scheduled(fixedDelay = "1h", initialDelay = "5m")
    void evictAbandoned() {
        LocalDateTime cutoff = LocalDateTime.now(clock).minusDays(7);
        byTokenHash.values().removeIf(pending -> pending.startedAt().isBefore(cutoff));
    }

    /** Both ends of a move, same as the JDBC adapter. */
    @Override
    public void purge(com.jrobertgardzinski.email.domain.Email email) {
        byTokenHash.values().removeIf(pending -> email.equals(pending.change().currentEmail())
                || email.equals(pending.change().newEmail()));
    }
}

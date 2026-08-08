package com.jrobertgardzinski;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.entity.SessionTokens;
import com.jrobertgardzinski.security.domain.repository.AuthorizationDataRepository;
import com.jrobertgardzinski.security.domain.vo.AccessGrant;
import com.jrobertgardzinski.security.domain.vo.ActiveSession;
import com.jrobertgardzinski.security.domain.vo.SessionFamily;
import com.jrobertgardzinski.security.domain.vo.SessionStatus;
import com.jrobertgardzinski.security.domain.vo.StoredSession;
import com.jrobertgardzinski.security.domain.vo.token.AccessToken;
import com.jrobertgardzinski.security.domain.vo.token.RefreshToken;
import io.micronaut.context.annotation.Requires;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;

import javax.sql.DataSource;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link AuthorizationDataRepository} used when no database is configured (tests). Indexes
 * sessions by a <strong>SHA-256 hash of the refresh token</strong>, never the raw value — the same
 * contract the JDBC adapter honours. Each row carries the access-token hash + expiry, the lineage
 * and a status; rotated rows are kept for theft detection. Raw tokens are not retained.
 */
@Singleton
@Requires(missingBeans = DataSource.class)
public final class InMemoryAuthorizationDataRepository implements AuthorizationDataRepository {

    private record Row(StoredSession session, String accessTokenHash, AccessGrant accessGrant) {}

    private final Map<String, Row> byRefreshTokenHash = new ConcurrentHashMap<>();

    /** The same clock the sessions were issued under — see {@link #listActiveSessions}. */
    private final java.time.Clock clock;

    public InMemoryAuthorizationDataRepository(java.time.Clock clock) {
        this.clock = clock;
    }

    /**
     * One monitor over every operation that changes a session lineage.
     *
     * <p>Each of them is individually atomic, and that is NOT the same as the transaction the JDBC
     * adapter runs inside. Between a rotation and the {@code create} that follows it, another
     * thread's {@code revokeFamily} can slip in — so the family is revoked and the successor is
     * then written anyway, leaving a live session in a lineage this service believes it destroyed.
     * A single lock makes the sequence behave like the transaction the other adapter gets for free.
     *
     * <p>It is now actually held. Until 2026-07-29 this field was declared, documented in exactly
     * these words, announced as the fix in commit 384b6a7 — and never read: the methods were
     * {@code synchronized} on {@code this} one at a time (which the {@link ConcurrentHashMap} below
     * already gave them), {@code revokeAllSessions} was not synchronized at all, and the rotation
     * reached the repository as two separate calls with nothing between them. The race the commit
     * announced as fixed was still exactly as open as before. Every mutator below takes THIS
     * monitor, and {@link #rotateAndCreate} holds it across both halves.
     */
    private final Object sessionLineage = new Object();

    @Override
    public SessionTokens create(SessionTokens sessionTokens, SessionFamily family) {
        synchronized (sessionLineage) {
            byRefreshTokenHash.put(
                    TokenHashing.hash(sessionTokens.refreshToken()),
                    new Row(
                            new StoredSession(sessionTokens.email(), sessionTokens.refreshTokenExpiration(),
                                    family, SessionStatus.ACTIVE),
                            TokenHashing.hash(sessionTokens.accessToken()),
                            new AccessGrant(sessionTokens.email(), sessionTokens.authorizationTokenExpiration())));
            return sessionTokens;
        }
    }

    /**
     * The rotation and the write of its successor, under the one monitor — the whole reason that
     * monitor exists. Reentrant, so the two calls it delegates to may keep taking it themselves.
     */
    @Override
    public Optional<SessionTokens> rotateAndCreate(RefreshToken presented,
                                                   java.util.function.Supplier<SessionTokens> successor,
                                                   SessionFamily family) {
        synchronized (sessionLineage) {
            return AuthorizationDataRepository.super.rotateAndCreate(presented, successor, family);
        }
    }

    @Override
    public Optional<StoredSession> findByRefreshToken(RefreshToken refreshToken) {
        return Optional.ofNullable(byRefreshTokenHash.get(TokenHashing.hash(refreshToken))).map(Row::session);
    }

    @Override
    public Optional<AccessGrant> findByAccessToken(AccessToken accessToken) {
        String hash = TokenHashing.hash(accessToken);
        return byRefreshTokenHash.values().stream()
                .filter(row -> row.accessTokenHash().equals(hash) && row.session().status() == SessionStatus.ACTIVE)
                .map(Row::accessGrant)
                .findFirst();
    }

    @Override
    public boolean markRotated(RefreshToken refreshToken) {
        // one atomic map operation, mirroring the conditional UPDATE the JDBC adapter runs: the
        // flip only happens from ACTIVE, so exactly one of two concurrent callers can win
        synchronized (sessionLineage) {
            boolean[] rotated = {false};
            byRefreshTokenHash.computeIfPresent(TokenHashing.hash(refreshToken), (hash, row) -> {
                StoredSession s = row.session();
                if (s.status() != SessionStatus.ACTIVE) {
                    return row;
                }
                rotated[0] = true;
                return new Row(new StoredSession(s.email(), s.refreshTokenExpiration(), s.family(),
                        SessionStatus.ROTATED), row.accessTokenHash(), row.accessGrant());
            });
            return rotated[0];
        }
    }

    @Override
    public void revokeFamily(SessionFamily family) {
        synchronized (sessionLineage) {
            byRefreshTokenHash.values().removeIf(row -> row.session().family().equals(family));
        }
    }

    /**
     * Drop sessions whose refresh token expired long enough ago to be beyond argument.
     *
     * <p>The twin with a datasource has {@code ExpiredSessionReaper} doing exactly this; without it
     * here, every sign-in ever made stays in the map for the life of the process. The GRACE is the
     * same idea as there — a day past expiry — so a row is never removed while anything could still
     * legitimately ask about it.
     */
    @Scheduled(fixedDelay = "1h", initialDelay = "5m")
    void evictExpired() {
        synchronized (sessionLineage) {
            byRefreshTokenHash.values().removeIf(row ->
                    row.session().refreshTokenExpiration().hasExpired(clock)
                            && row.session().refreshTokenExpiration().value()
                                    .isBefore(java.time.LocalDateTime.now(clock).minusDays(1)));
        }
    }

    @Override
    public void revokeAllSessions(Email email) {
        synchronized (sessionLineage) {
            byRefreshTokenHash.values().removeIf(row -> row.session().email().equals(email));
        }
    }

    /**
     * Active AND not yet expired — the clock is part of the answer, not a detail.
     *
     * <p>This used to filter on the status alone, in both adapters, while the reaper that removes
     * expired rows deliberately keeps them for the longer of the refresh window and 24 hours. So a
     * device that had stopped working a day ago was still listed as somewhere the user is signed in,
     * on the page they open precisely when they are worried that somebody else is.
     */
    @Override
    public java.util.List<ActiveSession> listActiveSessions(Email email) {
        return byRefreshTokenHash.values().stream()
                .map(Row::session)
                .filter(s -> s.email().equals(email) && s.status() == SessionStatus.ACTIVE
                        && !s.refreshTokenExpiration().hasExpired(clock))
                .map(s -> new ActiveSession(s.family(), s.refreshTokenExpiration()))
                .toList();
    }
}

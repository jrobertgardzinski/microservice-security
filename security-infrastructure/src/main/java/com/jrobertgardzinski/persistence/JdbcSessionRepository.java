package com.jrobertgardzinski.persistence;

import com.jrobertgardzinski.TokenHashing;
import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.session.SessionTokens;
import com.jrobertgardzinski.security.domain.session.SessionRepository;
import com.jrobertgardzinski.security.domain.session.AccessGrant;
import com.jrobertgardzinski.security.domain.session.SessionFamily;
import com.jrobertgardzinski.security.domain.session.SessionStatus;
import com.jrobertgardzinski.security.domain.session.StoredSession;
import com.jrobertgardzinski.security.domain.session.AccessToken;
import com.jrobertgardzinski.security.domain.session.RefreshToken;
import com.jrobertgardzinski.security.domain.session.AccessTokenExpiration;
import com.jrobertgardzinski.security.domain.session.RefreshTokenExpiration;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import javax.sql.DataSource;
import java.util.Optional;

/**
 * PostgreSQL-backed {@link SessionRepository}. Sessions are indexed by a SHA-256 hash of
 * the refresh token (see {@link TokenHashing}); the access token's hash is stored alongside, with
 * the lineage and status, so a presented access token can be authorized (active rows only) and a
 * replayed rotated refresh token can be detected. Raw tokens are never stored.
 */
@Singleton
@Requires(beans = DataSource.class)
final class JdbcSessionRepository implements SessionRepository {

    private final SessionJdbcRepository repository;

    /** The same clock the sessions were issued under — see {@link #listActiveSessions}. */
    private final java.time.Clock clock;

    JdbcSessionRepository(SessionJdbcRepository repository, java.time.Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public SessionTokens create(SessionTokens sessionTokens, SessionFamily family) {
        // A successor inherits the lineage's start; a brand-new family starts its clock now. This
        // is what makes the absolute lifetime absolute — reset it here and every refresh would buy
        // another full one, which is the behaviour the ceiling exists to end.
        java.time.LocalDateTime familyStartedAt = repository.familyStartedAt(family.value())
                .orElseGet(() -> java.time.LocalDateTime.now(clock));
        repository.save(new SessionEntity(
                TokenHashing.hash(sessionTokens.refreshToken()),
                sessionTokens.email().value(),
                sessionTokens.refreshTokenExpiration().value(),
                TokenHashing.hash(sessionTokens.accessToken()),
                sessionTokens.accessTokenExpiration().value(),
                family.value(),
                SessionStatus.ACTIVE.name(),
                familyStartedAt));
        return sessionTokens;
    }

    @Override
    public Optional<StoredSession> findByRefreshToken(RefreshToken refreshToken) {
        return repository.findById(TokenHashing.hash(refreshToken))
                .map(entity -> new StoredSession(
                        Email.of(entity.email()),
                        new RefreshTokenExpiration(entity.refreshTokenExpiration()),
                        new SessionFamily(entity.familyId()),
                        SessionStatus.valueOf(entity.status()),
                        entity.familyStartedAt()));
    }

    @Override
    public Optional<AccessGrant> findByAccessToken(AccessToken accessToken) {
        return repository.findByAccessTokenHashAndStatus(TokenHashing.hash(accessToken), SessionStatus.ACTIVE.name())
                .map(entity -> new AccessGrant(
                        Email.of(entity.email()), new AccessTokenExpiration(entity.accessTokenExpiration())));
    }

    @Override
    public boolean markRotated(RefreshToken refreshToken) {
        return repository.rotateIfActive(TokenHashing.hash(refreshToken)) > 0;
    }

    /**
     * Lock the lineage, THEN delete it — two statements on purpose.
     *
     * <p>One statement is not enough and a transaction does not make it enough: see
     * {@link SessionJdbcRepository#lockFamily}. A concurrent refresh that is between its rotation
     * and the write of its successor would otherwise leave that successor alive in a family this
     * call has just reported as destroyed, which is theft detection failing in the one case it
     * exists for.
     */
    @Override
    public void revokeFamily(SessionFamily family) {
        repository.lockFamily(family.value());
        repository.deleteByFamilyId(family.value());
    }

    /** "Log out everywhere", with the same two steps for the same reason. */
    @Override
    public void revokeAllSessions(Email email) {
        repository.lockSessionsOf(email.value());
        repository.deleteByEmail(email.value());
    }

    /** Active by status AND unexpired by the clock — see the query's javadoc for why both. */
    @Override
    public java.util.List<com.jrobertgardzinski.security.domain.session.ActiveSession> listActiveSessions(Email email) {
        return repository.findByEmailAndStatusAndRefreshTokenExpirationAfter(
                        email.value(), SessionStatus.ACTIVE.name(), java.time.LocalDateTime.now(clock)).stream()
                .map(entity -> new com.jrobertgardzinski.security.domain.session.ActiveSession(
                        new SessionFamily(entity.familyId()),
                        new RefreshTokenExpiration(entity.refreshTokenExpiration())))
                .toList();
    }
}

package com.jrobertgardzinski.persistence;

import com.jrobertgardzinski.TokenHashing;
import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.entity.SessionTokens;
import com.jrobertgardzinski.security.domain.repository.AuthorizationDataRepository;
import com.jrobertgardzinski.security.domain.vo.AccessGrant;
import com.jrobertgardzinski.security.domain.vo.SessionFamily;
import com.jrobertgardzinski.security.domain.vo.SessionStatus;
import com.jrobertgardzinski.security.domain.vo.StoredSession;
import com.jrobertgardzinski.security.domain.vo.token.AccessToken;
import com.jrobertgardzinski.security.domain.vo.token.RefreshToken;
import com.jrobertgardzinski.security.domain.vo.token.expiration.AuthorizationTokenExpiration;
import com.jrobertgardzinski.security.domain.vo.token.expiration.RefreshTokenExpiration;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import javax.sql.DataSource;
import java.util.Optional;

/**
 * PostgreSQL-backed {@link AuthorizationDataRepository}. Sessions are indexed by a SHA-256 hash of
 * the refresh token (see {@link TokenHashing}); the access token's hash is stored alongside, with
 * the lineage and status, so a presented access token can be authorized (active rows only) and a
 * replayed rotated refresh token can be detected. Raw tokens are never stored.
 */
@Singleton
@Requires(beans = DataSource.class)
final class JdbcAuthorizationDataRepository implements AuthorizationDataRepository {

    private final SessionJdbcRepository repository;

    /** The same clock the sessions were issued under — see {@link #listActiveSessions}. */
    private final java.time.Clock clock;

    JdbcAuthorizationDataRepository(SessionJdbcRepository repository, java.time.Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public SessionTokens create(SessionTokens sessionTokens, SessionFamily family) {
        repository.save(new SessionEntity(
                TokenHashing.hash(sessionTokens.refreshToken()),
                sessionTokens.email().value(),
                sessionTokens.refreshTokenExpiration().value(),
                TokenHashing.hash(sessionTokens.accessToken()),
                sessionTokens.authorizationTokenExpiration().value(),
                family.value(),
                SessionStatus.ACTIVE.name()));
        return sessionTokens;
    }

    @Override
    public Optional<StoredSession> findByRefreshToken(RefreshToken refreshToken) {
        return repository.findById(TokenHashing.hash(refreshToken))
                .map(entity -> new StoredSession(
                        Email.of(entity.email()),
                        new RefreshTokenExpiration(entity.refreshTokenExpiration()),
                        new SessionFamily(entity.familyId()),
                        SessionStatus.valueOf(entity.status())));
    }

    @Override
    public Optional<AccessGrant> findByAccessToken(AccessToken accessToken) {
        return repository.findByAccessTokenHashAndStatus(TokenHashing.hash(accessToken), SessionStatus.ACTIVE.name())
                .map(entity -> new AccessGrant(
                        Email.of(entity.email()), new AuthorizationTokenExpiration(entity.accessTokenExpiration())));
    }

    @Override
    public boolean markRotated(RefreshToken refreshToken) {
        return repository.rotateIfActive(TokenHashing.hash(refreshToken)) > 0;
    }

    @Override
    public void revokeFamily(SessionFamily family) {
        repository.deleteByFamilyId(family.value());
    }

    @Override
    public void revokeAllSessions(Email email) {
        repository.deleteByEmail(email.value());
    }

    /** Active by status AND unexpired by the clock — see the query's javadoc for why both. */
    @Override
    public java.util.List<com.jrobertgardzinski.security.domain.vo.ActiveSession> listActiveSessions(Email email) {
        return repository.findByEmailAndStatusAndRefreshTokenExpirationAfter(
                        email.value(), SessionStatus.ACTIVE.name(), java.time.LocalDateTime.now(clock)).stream()
                .map(entity -> new com.jrobertgardzinski.security.domain.vo.ActiveSession(
                        new SessionFamily(entity.familyId()),
                        new RefreshTokenExpiration(entity.refreshTokenExpiration())))
                .toList();
    }
}

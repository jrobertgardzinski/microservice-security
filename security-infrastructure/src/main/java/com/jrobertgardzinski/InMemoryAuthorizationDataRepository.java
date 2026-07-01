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

    @Override
    public SessionTokens create(SessionTokens sessionTokens, SessionFamily family) {
        byRefreshTokenHash.put(
                TokenHashing.hash(sessionTokens.refreshToken()),
                new Row(
                        new StoredSession(sessionTokens.email(), sessionTokens.refreshTokenExpiration(),
                                family, SessionStatus.ACTIVE),
                        TokenHashing.hash(sessionTokens.accessToken()),
                        new AccessGrant(sessionTokens.email(), sessionTokens.authorizationTokenExpiration())));
        return sessionTokens;
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
    public void markRotated(RefreshToken refreshToken) {
        byRefreshTokenHash.computeIfPresent(TokenHashing.hash(refreshToken), (hash, row) -> {
            StoredSession s = row.session();
            return new Row(new StoredSession(s.email(), s.refreshTokenExpiration(), s.family(), SessionStatus.ROTATED),
                    row.accessTokenHash(), row.accessGrant());
        });
    }

    @Override
    public void revokeFamily(SessionFamily family) {
        byRefreshTokenHash.values().removeIf(row -> row.session().family().equals(family));
    }

    @Override
    public void revokeAllSessions(Email email) {
        byRefreshTokenHash.values().removeIf(row -> row.session().email().equals(email));
    }

    @Override
    public java.util.List<ActiveSession> listActiveSessions(Email email) {
        return byRefreshTokenHash.values().stream()
                .map(Row::session)
                .filter(s -> s.email().equals(email) && s.status() == SessionStatus.ACTIVE)
                .map(s -> new ActiveSession(s.family(), s.refreshTokenExpiration()))
                .toList();
    }
}

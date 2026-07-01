package com.jrobertgardzinski.security.application.feature.support;

import com.jrobertgardzinski.security.domain.entity.SessionTokens;
import com.jrobertgardzinski.security.domain.repository.AuthorizationDataRepository;
import com.jrobertgardzinski.security.domain.vo.AccessGrant;
import com.jrobertgardzinski.security.domain.vo.SessionFamily;
import com.jrobertgardzinski.security.domain.vo.SessionStatus;
import com.jrobertgardzinski.security.domain.vo.StoredSession;
import com.jrobertgardzinski.security.domain.vo.token.AccessToken;
import com.jrobertgardzinski.security.domain.vo.token.RefreshToken;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory sessions keyed by refresh token (raw, since this is test support). Tracks lineage and
 * status so the use case can rotate and detect refresh-token reuse.
 */
public final class InMemoryAuthorizationDataRepository implements AuthorizationDataRepository {

    private record Row(SessionTokens tokens, SessionFamily family, SessionStatus status) {}

    private final Map<String, Row> byRefreshToken = new HashMap<>();

    /** Test seam: seed a stored active session in its own family. */
    public void store(SessionTokens session) {
        create(session, SessionFamily.start());
    }

    @Override
    public SessionTokens create(SessionTokens sessionTokens, SessionFamily family) {
        byRefreshToken.put(sessionTokens.refreshToken().value(), new Row(sessionTokens, family, SessionStatus.ACTIVE));
        return sessionTokens;
    }

    @Override
    public Optional<StoredSession> findByRefreshToken(RefreshToken refreshToken) {
        return Optional.ofNullable(byRefreshToken.get(refreshToken.value()))
                .map(row -> new StoredSession(
                        row.tokens().email(), row.tokens().refreshTokenExpiration(), row.family(), row.status()));
    }

    @Override
    public Optional<AccessGrant> findByAccessToken(AccessToken accessToken) {
        return byRefreshToken.values().stream()
                .filter(row -> row.tokens().accessToken().equals(accessToken) && row.status() == SessionStatus.ACTIVE)
                .map(row -> new AccessGrant(row.tokens().email(), row.tokens().authorizationTokenExpiration()))
                .findFirst();
    }

    @Override
    public void markRotated(RefreshToken refreshToken) {
        byRefreshToken.computeIfPresent(refreshToken.value(),
                (token, row) -> new Row(row.tokens(), row.family(), SessionStatus.ROTATED));
    }

    @Override
    public void revokeFamily(SessionFamily family) {
        byRefreshToken.values().removeIf(row -> row.family().equals(family));
    }
}

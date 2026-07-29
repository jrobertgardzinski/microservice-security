package com.jrobertgardzinski.security.application.feature.support;

import com.jrobertgardzinski.email.domain.Email;
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

    /**
     * The scenarios' clock, because {@link #listActiveSessions} is answerable only against one.
     * A double that ignores expiry would let a feature file describe behaviour neither production
     * adapter has — which is the one thing a double must never do.
     */
    private final java.time.Clock clock;

    public InMemoryAuthorizationDataRepository(java.time.Clock clock) {
        this.clock = clock;
    }

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
    public boolean markRotated(RefreshToken refreshToken) {
        // one atomic map operation, mirroring the conditional UPDATE the JDBC adapter runs: the
        // flip only happens from ACTIVE, so exactly one of two concurrent callers can win
        boolean[] rotated = {false};
        byRefreshToken.computeIfPresent(refreshToken.value(), (token, row) -> {
            if (row.status() != SessionStatus.ACTIVE) {
                return row;
            }
            rotated[0] = true;
            return new Row(row.tokens(), row.family(), SessionStatus.ROTATED);
        });
        return rotated[0];
    }

    @Override
    public void revokeFamily(SessionFamily family) {
        byRefreshToken.values().removeIf(row -> row.family().equals(family));
    }

    @Override
    public void revokeAllSessions(Email email) {
        byRefreshToken.values().removeIf(row -> row.tokens().email().equals(email));
    }

    /** Active by status AND unexpired by the clock, exactly as both production adapters answer. */
    @Override
    public java.util.List<com.jrobertgardzinski.security.domain.vo.ActiveSession> listActiveSessions(Email email) {
        return byRefreshToken.values().stream()
                .filter(row -> row.tokens().email().equals(email) && row.status() == SessionStatus.ACTIVE
                        && !row.tokens().refreshTokenExpiration().hasExpired(clock))
                .map(row -> new com.jrobertgardzinski.security.domain.vo.ActiveSession(
                        row.family(), row.tokens().refreshTokenExpiration()))
                .toList();
    }
}

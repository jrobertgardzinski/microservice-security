package com.jrobertgardzinski.security.domain.repository;

import com.jrobertgardzinski.security.domain.entity.SessionTokens;
import com.jrobertgardzinski.security.domain.vo.AccessGrant;
import com.jrobertgardzinski.security.domain.vo.SessionFamily;
import com.jrobertgardzinski.security.domain.vo.StoredSession;
import com.jrobertgardzinski.security.domain.vo.token.AccessToken;
import com.jrobertgardzinski.security.domain.vo.token.RefreshToken;

import java.util.Optional;

/**
 * Stores the sessions issued to users, keyed by their refresh token — the credential a client
 * presents to refresh. Keying by the token (not the user) lets a user hold several concurrent
 * sessions and lets a cookie-only refresh find its session without the client naming the user.
 *
 * <p>Rotated sessions are kept (marked {@link com.jrobertgardzinski.security.domain.vo.SessionStatus#ROTATED}),
 * not deleted, so a replayed refresh token can be recognised as theft and the whole
 * {@link SessionFamily} revoked. How tokens are matched (e.g. by a stored hash) is left to the
 * implementation.
 */
public interface AuthorizationDataRepository {

    SessionTokens create(SessionTokens sessionTokens, SessionFamily family);

    Optional<StoredSession> findByRefreshToken(RefreshToken refreshToken);

    Optional<AccessGrant> findByAccessToken(AccessToken accessToken);

    /** Mark the session holding this refresh token as rotated; it survives for reuse detection. */
    void markRotated(RefreshToken refreshToken);

    /** Revoke an entire session lineage (logout, or theft detected). */
    void revokeFamily(SessionFamily family);
}

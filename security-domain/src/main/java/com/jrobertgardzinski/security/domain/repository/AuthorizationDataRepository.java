package com.jrobertgardzinski.security.domain.repository;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.entity.SessionTokens;
import com.jrobertgardzinski.security.domain.vo.AccessGrant;
import com.jrobertgardzinski.security.domain.vo.ActiveSession;
import com.jrobertgardzinski.security.domain.vo.SessionFamily;
import com.jrobertgardzinski.security.domain.vo.StoredSession;
import com.jrobertgardzinski.security.domain.vo.token.AccessToken;
import com.jrobertgardzinski.security.domain.vo.token.RefreshToken;

import java.util.List;
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

    /**
     * Rotate the session holding this refresh token out, but ONLY if it is still active; the row
     * survives for reuse detection either way.
     *
     * <p>Returns whether THIS caller performed the rotation. That return value is the whole point:
     * the check and the write used to be separate statements, so two genuinely concurrent refreshes
     * both read ACTIVE, both wrote ROTATED (the second overwriting the first without complaint) and
     * both minted a successor — one session forked into two live chains, and a thief refreshing
     * alongside the victim got an undetectable lineage of their own. Implementations must make this
     * a single conditional write, so exactly one caller can win.
     *
     * @return {@code true} if this call rotated an ACTIVE session, {@code false} if somebody else
     *         already had — which the caller must treat exactly like a replayed token
     */
    boolean markRotated(RefreshToken refreshToken);

    /** Revoke an entire session lineage (logout, or theft detected). */
    void revokeFamily(SessionFamily family);

    /** Revoke every session of a user, across all lineages ("log out everywhere"). */
    void revokeAllSessions(Email email);

    /** The user's currently active sessions (for showing devices/sessions). */
    List<ActiveSession> listActiveSessions(Email email);
}

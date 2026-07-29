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

    /**
     * Rotate the presented token out and, if this caller won that rotation, write its successor —
     * as ONE step that nothing else in the lineage may interleave with.
     *
     * <p>The pair has to be indivisible, and saying so in a comment was not enough. Between the
     * rotation and the write, another thread detecting reuse of the same lineage can run
     * {@link #revokeFamily}: the family is destroyed and the successor is then written into it
     * anyway, leaving a LIVE session in a lineage this service believes it has revoked. That is the
     * theft-detection guarantee failing in the one case it exists for — a thief refreshing beside
     * the victim keeps a working session, and every device list and revoke says the family is gone.
     *
     * <p><b>A transaction is NOT enough, and this javadoc said it was for half a day.</b> The claim
     * was that the JDBC adapter needs nothing because {@code RefreshController} wraps the whole use
     * case in one — which is true and irrelevant. Under READ COMMITTED a concurrent
     * {@code revokeFamily} takes its DELETE snapshot when that statement starts, blocks on the row
     * this rotation locked, and on unblocking re-evaluates only that row: the successor inserted in
     * the meantime is invisible to it and survives, ACTIVE, in a lineage the service has just
     * reported as revoked. Reproduced on PostgreSQL 16 rather than argued.
     *
     * <p>So both adapters close it, each in its own idiom: the in-memory one — which is the
     * production wiring wherever no datasource is configured, not merely a test double — holds a
     * single monitor across both halves and the revokes; the JDBC one locks the lineage's rows in a
     * separate statement before deleting them ({@code SessionJdbcRepository#lockFamily}), so the
     * delete that follows is a new statement with a new snapshot and takes the successor too.
     *
     * @param successor supplied rather than passed, so that a caller which loses the rotation never
     *                  mints tokens it must throw away — and so the critical section demonstrably
     *                  spans the write, which is what makes the atomicity testable
     * @return the successor as stored, or empty when somebody else rotated first — which the caller
     *         must treat exactly like a replayed token
     */
    default Optional<SessionTokens> rotateAndCreate(RefreshToken presented,
                                                    java.util.function.Supplier<SessionTokens> successor,
                                                    SessionFamily family) {
        return markRotated(presented) ? Optional.of(create(successor.get(), family)) : Optional.empty();
    }

    /** Revoke an entire session lineage (logout, or theft detected). */
    void revokeFamily(SessionFamily family);

    /** Revoke every session of a user, across all lineages ("log out everywhere"). */
    void revokeAllSessions(Email email);

    /**
     * The user's sessions that are genuinely usable right now — for the "where am I signed in?"
     * page.
     *
     * <p>ACTIVE is a status, not a fact about time. A row keeps that status until the reaper
     * removes it, and the reaper deliberately waits: it holds expired rows for the longer of the
     * refresh window and 24 hours, plus up to its own hour-long interval. Filtering on the status
     * alone therefore showed a device that could not sign anything for a day after it stopped
     * working, on the page a user opens exactly when they are frightened that someone else is on
     * their account — and no revoke could clear it, because there was nothing left to revoke.
     * Implementations must apply the expiry as well, against the same clock the sessions were
     * issued under.
     */
    List<ActiveSession> listActiveSessions(Email email);
}

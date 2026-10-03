package com.jrobertgardzinski.security.application.session;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.application.TransactionBoundary;
import com.jrobertgardzinski.security.domain.entity.SessionTokens;
import com.jrobertgardzinski.security.domain.vo.ActiveSession;
import com.jrobertgardzinski.security.domain.vo.SessionRefreshRequest;
import com.jrobertgardzinski.security.domain.vo.token.RefreshToken;
import com.jrobertgardzinski.security.system.session.ListActiveSessions;
import com.jrobertgardzinski.security.system.session.Logout;
import com.jrobertgardzinski.security.system.session.RefreshSession;
import com.jrobertgardzinski.security.system.session.RefreshSessionResult;
import com.jrobertgardzinski.security.system.session.RevokeAllSessions;

import java.util.List;

/**
 * A signed-in user's sessions: rotating one, ending one, listing them, ending all of them. The
 * refresh token arrives as the string the client presented; the user is found from it, so the
 * client never names itself.
 */
public final class SessionService {

    private final RefreshSession refreshSession;
    private final Logout logout;
    private final ListActiveSessions listActiveSessions;
    private final RevokeAllSessions revokeAllSessions;
    private final TransactionBoundary transactionBoundary;

    public SessionService(RefreshSession refreshSession, Logout logout, ListActiveSessions listActiveSessions,
                          RevokeAllSessions revokeAllSessions, TransactionBoundary transactionBoundary) {
        this.refreshSession = refreshSession;
        this.logout = logout;
        this.listActiveSessions = listActiveSessions;
        this.revokeAllSessions = revokeAllSessions;
        this.transactionBoundary = transactionBoundary;
    }

    public Refresh refresh(String presentedToken) {
        SessionRefreshRequest request = new SessionRefreshRequest(new RefreshToken(presentedToken));
        return switch (transactionBoundary.execute(() -> refreshSession.execute(request))) {
            case RefreshSessionResult.Refreshed refreshed -> new Refresh.Refreshed(refreshed.sessionTokens());
            // expired, unknown or replayed: one refusal, so the answer never says whether the token existed
            case RefreshSessionResult.Expired expired -> new Refresh.Refused();
            case RefreshSessionResult.NotFound notFound -> new Refresh.Refused();
            case RefreshSessionResult.ReuseDetected reuseDetected -> new Refresh.Refused();
        };
    }

    /** Ends the session the token belongs to; idempotent, as there may be nothing to end. */
    public void logout(String presentedToken) {
        transactionBoundary.execute(() -> {
            logout.execute(new RefreshToken(presentedToken));
            return null;
        });
    }

    public List<ActiveSession> list(Email caller) {
        return listActiveSessions.execute(caller);
    }

    /** Logs out everywhere — the session asking included. */
    public void revokeAll(Email caller) {
        transactionBoundary.execute(() -> {
            revokeAllSessions.execute(caller);
            return null;
        });
    }

    public sealed interface Refresh {

        record Refreshed(SessionTokens sessionTokens) implements Refresh {}

        record Refused() implements Refresh {}
    }
}

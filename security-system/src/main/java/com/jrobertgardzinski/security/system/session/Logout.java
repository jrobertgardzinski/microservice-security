package com.jrobertgardzinski.security.system.session;

import com.jrobertgardzinski.security.domain.repository.SessionRepository;
import com.jrobertgardzinski.security.domain.vo.token.RefreshToken;

/**
 * Ends a session: the refresh token names the session, and removing it invalidates the whole
 * session at once — its refresh token can no longer be refreshed and its access token (whose hash
 * lived on the same record) no longer authorizes. Idempotent: logging out an unknown or already
 * removed session is a no-op.
 */
public class Logout {

    private final SessionRepository sessionRepository;

    public Logout(SessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    public void execute(RefreshToken refreshToken) {
        // end the whole lineage, not just this token, so no rotated remnant lingers
        sessionRepository.findByRefreshToken(refreshToken)
                .ifPresent(session -> sessionRepository.revokeFamily(session.family()));
    }
}

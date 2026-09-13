package com.jrobertgardzinski.security.system.session;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.repository.SessionRepository;

/**
 * Logs a user out everywhere: revokes every session the user holds, across all lineages, so no
 * refresh token can be refreshed and no access token authorizes any longer. Idempotent: a user
 * with no sessions is a no-op.
 */
public class RevokeAllSessions {

    private final SessionRepository sessionRepository;

    public RevokeAllSessions(SessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    public void execute(Email email) {
        sessionRepository.revokeAllSessions(email);
    }
}

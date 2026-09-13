package com.jrobertgardzinski.security.system.session;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.repository.SessionRepository;
import com.jrobertgardzinski.security.domain.vo.ActiveSession;

import java.util.List;

/**
 * Lists a user's currently active sessions, so they can see where they are signed in and choose to
 * revoke them.
 */
public class ListActiveSessions {

    private final SessionRepository sessionRepository;

    public ListActiveSessions(SessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    public List<ActiveSession> execute(Email email) {
        return sessionRepository.listActiveSessions(email);
    }
}

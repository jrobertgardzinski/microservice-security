package com.jrobertgardzinski.security.system.session;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.repository.AuthorizationDataRepository;
import com.jrobertgardzinski.security.domain.vo.ActiveSession;

import java.util.List;

/**
 * Lists a user's currently active sessions, so they can see where they are signed in and choose to
 * revoke them.
 */
public class ListActiveSessions {

    private final AuthorizationDataRepository authorizationDataRepository;

    public ListActiveSessions(AuthorizationDataRepository authorizationDataRepository) {
        this.authorizationDataRepository = authorizationDataRepository;
    }

    public List<ActiveSession> execute(Email email) {
        return authorizationDataRepository.listActiveSessions(email);
    }
}

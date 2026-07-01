package com.jrobertgardzinski.security.system.session;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.repository.AuthorizationDataRepository;

/**
 * Logs a user out everywhere: revokes every session the user holds, across all lineages, so no
 * refresh token can be refreshed and no access token authorizes any longer. Idempotent: a user
 * with no sessions is a no-op.
 */
public class RevokeAllSessions {

    private final AuthorizationDataRepository authorizationDataRepository;

    public RevokeAllSessions(AuthorizationDataRepository authorizationDataRepository) {
        this.authorizationDataRepository = authorizationDataRepository;
    }

    public void execute(Email email) {
        authorizationDataRepository.revokeAllSessions(email);
    }
}

package com.jrobertgardzinski.security.system.account;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.repository.AuthorizationDataRepository;
import com.jrobertgardzinski.security.domain.repository.UserRepository;

/**
 * Closes a user's account (GDPR right to be forgotten): revokes every session and deletes the user,
 * so the account can no longer authenticate and its access tokens stop authorizing. Idempotent.
 */
public class DeleteAccount {

    private final UserRepository userRepository;
    private final AuthorizationDataRepository authorizationDataRepository;

    public DeleteAccount(UserRepository userRepository, AuthorizationDataRepository authorizationDataRepository) {
        this.userRepository = userRepository;
        this.authorizationDataRepository = authorizationDataRepository;
    }

    public void execute(Email email) {
        authorizationDataRepository.revokeAllSessions(email);
        userRepository.deleteByEmail(email);
    }
}

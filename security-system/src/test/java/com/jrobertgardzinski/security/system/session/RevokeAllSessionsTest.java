package com.jrobertgardzinski.security.system.session;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.repository.AuthorizationDataRepository;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import net.jqwik.api.Example;
import net.jqwik.api.Label;
import net.jqwik.api.lifecycle.BeforeTry;
import org.mockito.Mockito;

@Epic("Use case")
@Feature("Revoke all sessions")
class RevokeAllSessionsTest {

    private static final Email EMAIL = Email.of("user@example.com");

    private AuthorizationDataRepository authorizationDataRepository;
    private RevokeAllSessions revokeAllSessions;

    @BeforeTry
    void init() {
        authorizationDataRepository = Mockito.mock(AuthorizationDataRepository.class);
        revokeAllSessions = new RevokeAllSessions(authorizationDataRepository);
    }

    @Example
    @Label("Revoking all sessions revokes every session of the user")
    void revokes_every_session_of_the_user() {
        revokeAllSessions.execute(EMAIL);

        Mockito.verify(authorizationDataRepository).revokeAllSessions(EMAIL);
    }
}

package com.jrobertgardzinski.security.system.session;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.repository.SessionRepository;
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

    private SessionRepository sessionRepository;
    private RevokeAllSessions revokeAllSessions;

    @BeforeTry
    void init() {
        sessionRepository = Mockito.mock(SessionRepository.class);
        revokeAllSessions = new RevokeAllSessions(sessionRepository);
    }

    @Example
    @Label("Revoking all sessions revokes every session of the user")
    void revokes_every_session_of_the_user() {
        revokeAllSessions.execute(EMAIL);

        Mockito.verify(sessionRepository).revokeAllSessions(EMAIL);
    }
}

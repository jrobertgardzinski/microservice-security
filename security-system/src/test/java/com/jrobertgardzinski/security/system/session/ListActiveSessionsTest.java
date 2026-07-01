package com.jrobertgardzinski.security.system.session;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.repository.AuthorizationDataRepository;
import com.jrobertgardzinski.security.domain.vo.ActiveSession;
import com.jrobertgardzinski.security.domain.vo.SessionFamily;
import com.jrobertgardzinski.security.domain.vo.token.expiration.RefreshTokenExpiration;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import net.jqwik.api.Example;
import net.jqwik.api.Label;
import net.jqwik.api.lifecycle.BeforeTry;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Epic("Use case")
@Feature("List active sessions")
class ListActiveSessionsTest {

    private static final Email EMAIL = Email.of("user@example.com");

    private AuthorizationDataRepository authorizationDataRepository;
    private ListActiveSessions listActiveSessions;

    @BeforeTry
    void init() {
        authorizationDataRepository = Mockito.mock(AuthorizationDataRepository.class);
        listActiveSessions = new ListActiveSessions(authorizationDataRepository);
    }

    @Example
    @Label("Returns the user's active sessions from the repository")
    void returns_the_active_sessions() {
        List<ActiveSession> sessions = List.of(
                new ActiveSession(SessionFamily.start(), new RefreshTokenExpiration(LocalDateTime.now().plusHours(1))),
                new ActiveSession(SessionFamily.start(), new RefreshTokenExpiration(LocalDateTime.now().plusHours(2))));
        Mockito.when(authorizationDataRepository.listActiveSessions(EMAIL)).thenReturn(sessions);

        assertEquals(sessions, listActiveSessions.execute(EMAIL));
    }
}

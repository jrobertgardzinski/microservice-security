package com.jrobertgardzinski.security.system.session;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.repository.AuthorizationDataRepository;
import com.jrobertgardzinski.security.domain.vo.SessionFamily;
import com.jrobertgardzinski.security.domain.vo.SessionStatus;
import com.jrobertgardzinski.security.domain.vo.StoredSession;
import com.jrobertgardzinski.security.domain.vo.token.RefreshToken;
import com.jrobertgardzinski.security.domain.vo.token.expiration.RefreshTokenExpiration;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import net.jqwik.api.Example;
import net.jqwik.api.Label;
import net.jqwik.api.lifecycle.BeforeTry;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Epic("Use case")
@Feature("Logout")
class LogoutTest {

    private static final RefreshToken TOKEN = new RefreshToken("refresh-token");
    private static final SessionFamily FAMILY = new SessionFamily(UUID.fromString("00000000-0000-0000-0000-000000000009"));

    private AuthorizationDataRepository authorizationDataRepository;
    private Logout logout;

    @BeforeTry
    void init() {
        authorizationDataRepository = Mockito.mock(AuthorizationDataRepository.class);
        logout = new Logout(authorizationDataRepository);
    }

    @Example
    @Label("Logging out revokes the whole family of the session named by the refresh token")
    void revokes_the_family() {
        Mockito.when(authorizationDataRepository.findByRefreshToken(TOKEN)).thenReturn(Optional.of(
                new StoredSession(Email.of("user@example.com"),
                        new RefreshTokenExpiration(LocalDateTime.now().plusHours(1)), FAMILY, SessionStatus.ACTIVE)));

        logout.execute(TOKEN);

        Mockito.verify(authorizationDataRepository).revokeFamily(FAMILY);
    }

    @Example
    @Label("Logging out an unknown session is a no-op")
    void unknown_session_is_a_no_op() {
        Mockito.when(authorizationDataRepository.findByRefreshToken(TOKEN)).thenReturn(Optional.empty());

        logout.execute(TOKEN);

        Mockito.verify(authorizationDataRepository, Mockito.never()).revokeFamily(Mockito.any());
    }
}

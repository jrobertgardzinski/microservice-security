package com.jrobertgardzinski.security.system.authorization;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.repository.AuthorizationDataRepository;
import com.jrobertgardzinski.security.domain.vo.AccessGrant;
import com.jrobertgardzinski.security.domain.vo.token.AccessToken;
import com.jrobertgardzinski.security.domain.vo.token.expiration.AuthorizationTokenExpiration;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import net.jqwik.api.Example;
import net.jqwik.api.Label;
import net.jqwik.api.lifecycle.BeforeTry;
import org.mockito.Mockito;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@Epic("Use case")
@Feature("Authorize")
class AuthorizeTest {

    private static final AccessToken TOKEN = new AccessToken("access-token");
    private static final Email EMAIL = Email.of("user@example.com");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    private AuthorizationDataRepository authorizationDataRepository;
    private Authorize authorize;

    @BeforeTry
    void init() {
        authorizationDataRepository = Mockito.mock(AuthorizationDataRepository.class);
        authorize = new Authorize(authorizationDataRepository, CLOCK);
    }

    @Example
    @Label("Authorized when the access token maps to an unexpired session")
    void authorized_when_token_valid() {
        grantExpiringAt(LocalDateTime.now(CLOCK).plusHours(1));

        AuthorizationResult result = authorize.execute(TOKEN);

        AuthorizationResult.Authorized authorized = assertInstanceOf(AuthorizationResult.Authorized.class, result);
        assertEquals(EMAIL, authorized.email());
    }

    @Example
    @Label("Unauthorized when the access token has expired")
    void unauthorized_when_token_expired() {
        grantExpiringAt(LocalDateTime.now(CLOCK).minusSeconds(1));

        AuthorizationResult result = authorize.execute(TOKEN);

        assertInstanceOf(AuthorizationResult.Unauthorized.class, result);
    }

    @Example
    @Label("Unauthorized when no session matches the access token")
    void unauthorized_when_token_unknown() {
        Mockito.when(authorizationDataRepository.findByAccessToken(TOKEN)).thenReturn(Optional.empty());

        AuthorizationResult result = authorize.execute(TOKEN);

        assertInstanceOf(AuthorizationResult.Unauthorized.class, result);
    }

    private void grantExpiringAt(LocalDateTime expiry) {
        Mockito.when(authorizationDataRepository.findByAccessToken(TOKEN))
                .thenReturn(Optional.of(new AccessGrant(EMAIL, new AuthorizationTokenExpiration(expiry))));
    }
}

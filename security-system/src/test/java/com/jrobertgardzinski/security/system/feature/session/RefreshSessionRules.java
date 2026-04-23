package com.jrobertgardzinski.security.system.feature.session;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.vo.AccessTokenValidityInHours;
import com.jrobertgardzinski.security.domain.vo.RefreshTokenValidityInHours;
import com.jrobertgardzinski.security.domain.vo.SessionTokensConfig;
import com.jrobertgardzinski.security.domain.entity.SessionTokens;
import com.jrobertgardzinski.security.domain.event.refresh.NoRefreshTokenFoundEvent;
import com.jrobertgardzinski.security.domain.event.refresh.RefreshTokenEvent;
import com.jrobertgardzinski.security.domain.event.refresh.RefreshTokenExpiredEvent;
import com.jrobertgardzinski.security.domain.event.refresh.RefreshTokenPassedEvent;
import com.jrobertgardzinski.security.domain.vo.*;
import com.jrobertgardzinski.security.domain.vo.token.RefreshToken;
import com.jrobertgardzinski.security.domain.vo.token.Token;
import com.jrobertgardzinski.security.domain.vo.token.TokenValidityInHours;
import com.jrobertgardzinski.security.system.feature.RefreshSession;
import com.jrobertgardzinski.security.system.stub.StubAuthorizationDataRepository;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

public class RefreshSessionRules {

    private static final SessionTokensConfig SESSION_TOKENS_CONFIG =
            new SessionTokensConfig(
                    new RefreshTokenValidityInHours(
                            new TokenValidityInHours(1)
                    ),
                    new AccessTokenValidityInHours(
                            new TokenValidityInHours(1)
                    ));

    private final RefreshSession refreshSession;
    private final StubAuthorizationDataRepository authorizationDataRepository;
    private final Clock clock = Clock.systemDefaultZone();

    private Email email;
    private RefreshToken refreshToken;
    private RefreshTokenEvent result;

    public RefreshSessionRules(StubAuthorizationDataRepository authorizationDataRepository) {
        this.authorizationDataRepository = authorizationDataRepository;
        this.refreshSession = new RefreshSession(authorizationDataRepository, clock, SESSION_TOKENS_CONFIG);
    }

    // background

    @Given("the system holds a session for email {string}")
    public void givenSystemHoldsSession(String emailValue) {
        email = Email.of(emailValue);
    }

    // given

    @Given("the session is active")
    public void givenActiveSession() {
        SessionTokens sessionTokens = SessionTokens.createFor(email,
                SESSION_TOKENS_CONFIG,
                clock);
        authorizationDataRepository.create(sessionTokens);
        refreshToken = sessionTokens.refreshToken();
    }

    @Given("the session is expired")
    public void givenExpiredSession() {
        Clock pastClock = Clock.fixed(
                LocalDateTime.now().minusHours(SESSION_TOKENS_CONFIG.refreshTokenValidityInHours().tokenValidityInHours().value() + 1)
                        .atZone(ZoneId.systemDefault()).toInstant(),
                ZoneId.systemDefault());
        SessionTokens session = SessionTokens.createFor(email,
                SESSION_TOKENS_CONFIG,
                pastClock);
        authorizationDataRepository.create(session);
        refreshToken = session.refreshToken();
    }

    @Given("the session does not exist")
    public void givenNoSession() {
        refreshToken = new RefreshToken(Token.random());
    }

    // when

    @When("the system processes a session refresh request")
    public void whenSystemProcessesRefresh() {
        SessionRefreshRequest request = new SessionRefreshRequest(email, refreshToken);
        result = refreshSession.apply(request);
    }

    // then

    @Then("the system returns new session tokens")
    public void thenReturnsNewTokens() {
        assertInstanceOf(RefreshTokenPassedEvent.class, result);
        RefreshTokenPassedEvent passed = (RefreshTokenPassedEvent) result;
        assertNotNull(passed.sessionTokens());
    }

    @Then("the system rejects with RefreshTokenExpiredEvent")
    public void thenExpiredEvent() {
        assertInstanceOf(RefreshTokenExpiredEvent.class, result);
    }

    @Then("the system rejects with NoRefreshTokenFoundEvent")
    public void thenNotFoundEvent() {
        assertInstanceOf(NoRefreshTokenFoundEvent.class, result);
    }
}

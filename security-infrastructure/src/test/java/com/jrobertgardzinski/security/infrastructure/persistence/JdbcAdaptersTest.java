package com.jrobertgardzinski.security.infrastructure.persistence;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.email.domain.NormalizedEmail;
import com.jrobertgardzinski.password.domain.HashedPassword;
import com.jrobertgardzinski.security.domain.entity.AuthenticationBlock;
import com.jrobertgardzinski.security.domain.entity.SessionTokens;
import com.jrobertgardzinski.security.domain.entity.User;
import com.jrobertgardzinski.security.domain.repository.AuthenticationBlockRepository;
import com.jrobertgardzinski.security.domain.repository.AuthorizationDataRepository;
import com.jrobertgardzinski.security.domain.repository.EmailAlreadyTakenException;
import com.jrobertgardzinski.security.domain.repository.RejectedAuthenticationRepository;
import com.jrobertgardzinski.security.domain.repository.UserRepository;
import com.jrobertgardzinski.security.domain.vo.AccessGrant;
import com.jrobertgardzinski.security.domain.vo.AccessTokenValidityInHours;
import com.jrobertgardzinski.security.domain.vo.IpAddress;
import com.jrobertgardzinski.security.domain.vo.RefreshTokenValidityInHours;
import com.jrobertgardzinski.security.domain.vo.RejectedAuthenticationDetails;
import com.jrobertgardzinski.security.domain.vo.SessionFamily;
import com.jrobertgardzinski.security.domain.vo.SessionStatus;
import com.jrobertgardzinski.security.domain.vo.SessionTokensConfig;
import com.jrobertgardzinski.security.domain.vo.StoredSession;
import com.jrobertgardzinski.security.domain.vo.token.AccessToken;
import com.jrobertgardzinski.security.domain.vo.token.RefreshToken;
import io.micronaut.context.ApplicationContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Drives the JDBC repository adapters against a real PostgreSQL (so the Micronaut Data mappings,
 * the snake_case columns, Flyway's schema and the refresh-token hashing are exercised for real).
 * Skipped automatically when no Docker is available. Beans are resolved by their domain port type:
 * a datasource is present, so the JDBC adapters win over the in-memory ones.
 */
@Testcontainers(disabledWithoutDocker = true)
class JdbcAdaptersTest {

    private static final SessionTokensConfig SESSION_CONFIG = new SessionTokensConfig(
            new RefreshTokenValidityInHours(24), new AccessTokenValidityInHours(1));

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static ApplicationContext context;

    @BeforeAll
    static void startContext() {
        context = ApplicationContext.run(Map.of(
                "datasources.default.url", POSTGRES.getJdbcUrl(),
                "datasources.default.username", POSTGRES.getUsername(),
                "datasources.default.password", POSTGRES.getPassword(),
                "datasources.default.driver-class-name", "org.postgresql.Driver",
                "datasources.default.dialect", "POSTGRES",
                "flyway.datasources.default.enabled", true));
    }

    @AfterAll
    static void stopContext() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    void users_are_saved_found_and_deduplicated() {
        UserRepository users = context.getBean(UserRepository.class);
        Email email = Email.of("jdbc-user@example.com");
        User user = new User(email, new HashedPassword("argon2-hash"));

        users.save(user);

        assertThat(users.findBy(email)).get().extracting(User::id).isEqualTo(user.id());
        assertThat(users.existsBy(NormalizedEmail.of(email))).isTrue();
        assertThat(users.findBy(Email.of("nobody@example.com"))).isEmpty();
        assertThat(users.existsBy(NormalizedEmail.of(Email.of("nobody@example.com")))).isFalse();
    }

    @Test
    void rejected_attempts_are_counted_within_a_window_and_cleared() {
        RejectedAuthenticationRepository rejected = context.getBean(RejectedAuthenticationRepository.class);
        IpAddress ip = new IpAddress("203.0.113.10");
        LocalDateTime at = LocalDateTime.now();
        rejected.create(new RejectedAuthenticationDetails(ip, at));
        rejected.create(new RejectedAuthenticationDetails(ip, at));

        assertThat(rejected.countFailuresBy(ip, at.minusMinutes(15)).count()).isEqualTo(2);
        assertThat(rejected.countFailuresBy(ip, at.plusMinutes(1)).count()).isZero();

        rejected.removeAllFor(ip);
        assertThat(rejected.countFailuresBy(ip, at.minusMinutes(15)).count()).isZero();
    }

    @Test
    void a_block_is_upserted_found_and_removed() {
        AuthenticationBlockRepository blocks = context.getBean(AuthenticationBlockRepository.class);
        IpAddress ip = new IpAddress("203.0.113.11");
        blocks.create(new AuthenticationBlock(ip, LocalDateTime.now().plusMinutes(5)));
        blocks.create(new AuthenticationBlock(ip, LocalDateTime.now().plusMinutes(10))); // upsert, must not collide

        assertThat(blocks.findBy(ip)).isPresent();

        blocks.removeAllFor(ip);
        assertThat(blocks.findBy(ip)).isEmpty();
    }

    @Test
    void a_session_is_found_by_refresh_token_then_rotated_and_its_family_revoked() {
        AuthorizationDataRepository sessions = context.getBean(AuthorizationDataRepository.class);
        SessionFamily family = SessionFamily.start();
        SessionTokens session = SessionTokens.createFor(
                Email.of("jdbc-session@example.com"), SESSION_CONFIG, Clock.systemUTC());

        sessions.create(session, family);

        StoredSession found = sessions.findByRefreshToken(session.refreshToken()).orElseThrow();
        assertThat(found.email().value()).isEqualTo("jdbc-session@example.com");
        assertThat(found.family()).isEqualTo(family);
        assertThat(found.status()).isEqualTo(SessionStatus.ACTIVE);
        assertThat(sessions.findByRefreshToken(RefreshToken.random())).isEmpty();

        sessions.markRotated(session.refreshToken());
        assertThat(sessions.findByRefreshToken(session.refreshToken()))
                .get().extracting(StoredSession::status).isEqualTo(SessionStatus.ROTATED);

        sessions.revokeFamily(family);
        assertThat(sessions.findByRefreshToken(session.refreshToken())).isEmpty();
    }

    @Test
    void an_access_token_authorizes_only_while_its_session_is_active() {
        AuthorizationDataRepository sessions = context.getBean(AuthorizationDataRepository.class);
        SessionTokens session = SessionTokens.createFor(
                Email.of("jdbc-access@example.com"), SESSION_CONFIG, Clock.systemUTC());

        sessions.create(session, SessionFamily.start());

        Optional<AccessGrant> grant = sessions.findByAccessToken(session.accessToken());
        assertThat(grant).isPresent();
        assertThat(grant.get().email().value()).isEqualTo("jdbc-access@example.com");
        assertThat(sessions.findByAccessToken(AccessToken.random())).isEmpty();

        sessions.markRotated(session.refreshToken());
        assertThat(sessions.findByAccessToken(session.accessToken())).isEmpty();
    }

    @Test
    void a_duplicate_email_is_rejected_by_the_unique_constraint() {
        UserRepository users = context.getBean(UserRepository.class);
        Email email = Email.of("jdbc-duplicate@example.com");
        users.save(new User(email, new HashedPassword("hash-a")));

        assertThatThrownBy(() -> users.save(new User(email, new HashedPassword("hash-b"))))
                .isInstanceOf(EmailAlreadyTakenException.class);
    }
}

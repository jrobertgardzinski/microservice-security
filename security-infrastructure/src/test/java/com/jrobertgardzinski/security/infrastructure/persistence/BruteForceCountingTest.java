package com.jrobertgardzinski.security.infrastructure.persistence;

import com.jrobertgardzinski.TransactionBoundary;
import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.password.domain.HashAlgorithmPort;
import com.jrobertgardzinski.password.domain.PlaintextPassword;
import com.jrobertgardzinski.security.domain.entity.User;
import com.jrobertgardzinski.security.domain.repository.EmailVerificationRepository;
import com.jrobertgardzinski.security.domain.repository.UserRepository;
import com.jrobertgardzinski.security.domain.vo.AuthenticationRequest;
import com.jrobertgardzinski.security.domain.vo.IpAddress;
import com.jrobertgardzinski.security.domain.vo.Source;
import com.jrobertgardzinski.security.system.authentication.Authentication;
import com.jrobertgardzinski.security.system.authentication.AuthenticationResult;
import io.micronaut.context.ApplicationContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The two counting rules of the brute-force guard, against a real PostgreSQL.
 *
 * <p>Both were pinned only by mocks, and a mock cannot be wrong about a query: the test said
 * "countFailuresOnAccount returns 3" and the guard behaved accordingly, whatever the SQL behind it
 * actually counted. What the rules protect is precisely the difference between two shapes of
 * attack, and the difference lives in a WHERE clause.
 *
 * <ul>
 *   <li>A correct password clears THIS PAIR's failures and nothing else. Clearing the whole
 *       address makes one known-good credential an amnesty for everything that address is trying;
 *       clearing nothing leaves an office locked out over one person's typos.</li>
 *   <li>The per-source ceiling sees what the pair counter cannot: a few guesses spread over many
 *       accounts, where no single account ever reaches its own limit.</li>
 * </ul>
 */
@Testcontainers(disabledWithoutDocker = true)
class BruteForceCountingTest {

    private static final String THEIR_PASSWORD = "TheirRealPassword1!";
    private static final String WRONG_PASSWORD = "NotTheirPassword1!";
    private static final int MAX_FAILURES = 3;
    private static final int MAX_FAILURES_PER_SOURCE = 5;

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18-alpine");

    static ApplicationContext context;

    @BeforeAll
    static void startContext() {
        context = ApplicationContext.run(Map.of(
                "datasources.default.url", POSTGRES.getJdbcUrl(),
                "datasources.default.username", POSTGRES.getUsername(),
                "datasources.default.password", POSTGRES.getPassword(),
                "datasources.default.driver-class-name", "org.postgresql.Driver",
                "datasources.default.dialect", "POSTGRES",
                "flyway.datasources.default.enabled", true,
                "security.brute.force.max.failures", MAX_FAILURES,
                "security.brute.force.max.failures.per.source", MAX_FAILURES_PER_SOURCE));
    }

    @AfterAll
    static void stopContext() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    @DisplayName("a correct password clears that pair's failures and leaves the address's others")
    void success_forgives_the_pair_and_only_the_pair() {
        Source source = new Source(new IpAddress("198.51.100.21"), "counting/1.0");
        Email mine = account("mine@example.com");
        Email theirs = account("theirs@example.com");

        // one miss on each account: below both limits, and deliberately from the same address
        attempt(source, mine, WRONG_PASSWORD);
        attempt(source, theirs, WRONG_PASSWORD);

        // I get my own password right
        assertThat(attempt(source, mine, THEIR_PASSWORD))
                .isInstanceOf(AuthenticationResult.Authenticated.class);

        // ...which forgives MY misses: two more are needed to reach the limit of three again
        attempt(source, mine, WRONG_PASSWORD);
        attempt(source, mine, WRONG_PASSWORD);
        assertThat(attempt(source, mine, WRONG_PASSWORD))
                .as("if my earlier miss still counted, the limit would have been reached one"
                        + " attempt sooner")
                .isNotInstanceOf(AuthenticationResult.Blocked.class);

        assertThat(attempt(source, mine, WRONG_PASSWORD))
                .as("and the fourth is where it does hold — a success is an amnesty, not an"
                        + " exemption")
                .isInstanceOf(AuthenticationResult.Blocked.class);
    }

    @Test
    @DisplayName("the per-source ceiling catches a spray no single account ever notices")
    void the_ceiling_sees_what_the_pair_counter_cannot() {
        Source source = new Source(new IpAddress("198.51.100.22"), "spray/1.0");

        // one guess each against five accounts: no pair ever reaches its own limit of three
        for (int i = 0; i < MAX_FAILURES_PER_SOURCE; i++) {
            Email victim = account("sprayed-" + i + "@example.com");
            assertThat(attempt(source, victim, WRONG_PASSWORD))
                    .as("attempt %d is a single miss on a fresh account — nothing to block yet", i)
                    .isNotInstanceOf(AuthenticationResult.Blocked.class);
        }

        assertThat(attempt(source, account("sprayed-last@example.com"), WRONG_PASSWORD))
                .as("five misses from one address, and the ceiling is what sees them: the pair"
                        + " counter is still at one for every account involved")
                .isInstanceOf(AuthenticationResult.Blocked.class);
    }

    private static AuthenticationResult attempt(Source source, Email email, String password) {
        Authentication authentication = context.getBean(Authentication.class);
        TransactionBoundary transactions = context.getBean(TransactionBoundary.class);
        return transactions.execute(() -> authentication.execute(
                new AuthenticationRequest(source, email, PlaintextPassword.of(password))));
    }

    /** A real, verified account with a known password — so a WRONG one is the only thing refused. */
    private static Email account(String address) {
        Email email = Email.of(address);
        UserRepository users = context.getBean(UserRepository.class);
        HashAlgorithmPort hashing = context.getBean(HashAlgorithmPort.class);
        context.getBean(TransactionBoundary.class).execute(() -> {
            users.save(new User(email, hashing.hash(PlaintextPassword.of(THEIR_PASSWORD))));
            context.getBean(EmailVerificationRepository.class).markVerified(email);
            return null;
        });
        return email;
    }
}

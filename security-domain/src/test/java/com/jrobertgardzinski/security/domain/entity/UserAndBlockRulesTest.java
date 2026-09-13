package com.jrobertgardzinski.security.domain.entity;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.password.domain.HashedPassword;
import com.jrobertgardzinski.security.domain.vo.FailuresCount;
import com.jrobertgardzinski.security.domain.vo.IpAddress;
import com.jrobertgardzinski.security.domain.vo.Role;
import com.jrobertgardzinski.security.domain.vo.Source;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Two small pieces of arithmetic the whole authorization story rests on, and which nothing asked
 * about: what a user's role set really contains, and whether a block is over.
 */
@Epic("Domain")
@Feature("Value rules")
class UserAndBlockRulesTest {

    private static final Email EMAIL = Email.of("rules@example.com");
    private static final HashedPassword HASH = new HashedPassword("argon2-hash");

    @Test
    @DisplayName("every user is a USER, whatever the grants say")
    void the_role_set_always_contains_user() {
        assertThat(new User(EMAIL, HASH).roles())
                .as("a fresh registration is a plain USER")
                .containsExactly(Role.USER);

        assertThat(new User(UUID.randomUUID(), EMAIL, HASH, null, Set.of(Role.ADMIN)).roles())
                .as("an admin is still signed in, so 'is a USER' must not be a different question"
                        + " from 'is signed in'")
                .containsExactlyInAnyOrder(Role.USER, Role.ADMIN);

        assertThat(new User(UUID.randomUUID(), EMAIL, HASH, null, Set.of()).roles())
                .as("an empty grant set is a USER, not a user with no authority at all")
                .containsExactly(Role.USER);

        assertThat(new User(UUID.randomUUID(), EMAIL, HASH, null, null).roles())
                .as("and so is none at all — the row's roles column may be absent")
                .containsExactly(Role.USER);
    }

    @Test
    @DisplayName("the role set cannot be edited from outside the user")
    void the_role_set_is_the_users_own() {
        java.util.Set<Role> granted = new java.util.HashSet<>(Set.of(Role.MODERATOR));
        User user = new User(UUID.randomUUID(), EMAIL, HASH, null, granted);

        granted.add(Role.ADMIN);

        assertThat(user.hasRole(Role.ADMIN))
                .as("the caller's own set is copied on construction; sharing it would let anything"
                        + " holding a reference grant itself authority afterwards")
                .isFalse();
        assertThat(user.hasRole(Role.MODERATOR)).isTrue();
    }

    @Test
    @DisplayName("a block is over the moment its expiry is reached, not a tick later")
    void the_edge_of_a_block_is_outside_it() {
        LocalDateTime expiry = LocalDateTime.of(2026, 9, 13, 12, 0);
        AuthenticationBlock block =
                new AuthenticationBlock(new Source(new IpAddress("203.0.113.9"), "curl/8.0"), expiry);

        assertThat(block.isStillActive(at(expiry.minusSeconds(1))))
                .as("a second before, the door is shut")
                .isTrue();
        assertThat(block.isStillActive(at(expiry)))
                .as("AT the expiry it is open — a block that outlived its own deadline would be a"
                        + " lockout nobody can explain")
                .isFalse();
        assertThat(block.isStillActive(at(expiry.plusSeconds(1)))).isFalse();
    }

    @Test
    @DisplayName("the limit is reached at the limit, not after it")
    void the_failure_limit_is_inclusive() {
        assertThat(new FailuresCount(4).hasReachedTheLimit(5))
                .as("one attempt short is still allowed to try")
                .isFalse();
        assertThat(new FailuresCount(5).hasReachedTheLimit(5))
                .as("'five attempts and you are blocked' must block on the fifth, not the sixth")
                .isTrue();
        assertThat(new FailuresCount(6).hasReachedTheLimit(5))
                .as("and a count that ran past the limit is past it, not back under")
                .isTrue();
    }

    private static Clock at(LocalDateTime moment) {
        return Clock.fixed(moment.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    }
}

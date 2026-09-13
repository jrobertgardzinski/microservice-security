package com.jrobertgardzinski.security.system.roles;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.password.domain.HashedPassword;
import com.jrobertgardzinski.security.domain.entity.User;
import com.jrobertgardzinski.security.domain.repository.UserRepository;
import com.jrobertgardzinski.security.domain.vo.Role;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Granting roles is itself an admin action, which is what makes removing the last ADMIN a door
 * locked from the inside: nobody left can grant it back.
 *
 * <p>The way out was always there — {@code security.bootstrap-admins} names identities that hold
 * ADMIN by configuration rather than by a row — which is exactly why the refusal is conditional. A
 * deployment that declares a bootstrap admin still has one after this change, and is allowed to go
 * ahead; one that declares none is about to have none at all.
 */
@Epic("Use case")
@Feature("Set user roles")
class SetUserRolesTest {

    private static final Email THE_ADMIN = Email.of("admin@example.com");

    @Test
    @DisplayName("the last ADMIN cannot strip their own grant")
    void the_last_admin_is_refused() {
        UserRepository users = usersWhere(THE_ADMIN, Set.of(Role.USER, Role.ADMIN), 1);

        SetUserRoles.Result result =
                new SetUserRoles(users, BootstrapAdmins.none()).execute(THE_ADMIN, Set.of(Role.USER));

        assertThat(result.status()).isEqualTo(SetUserRoles.Status.WOULD_LEAVE_NO_ADMIN);
        assertThat(result.roles())
                .as("and the answer carries what they still hold, so a client can show the truth")
                .contains(Role.ADMIN);
        Mockito.verify(users, Mockito.never()).setRoles(Mockito.any(), Mockito.any());
    }

    @Test
    @DisplayName("with a bootstrap admin declared, there is still an admin afterwards")
    void a_deployment_with_a_bootstrap_admin_may_go_ahead() {
        UserRepository users = usersWhere(THE_ADMIN, Set.of(Role.USER, Role.ADMIN), 1);
        BootstrapAdmins declared = BootstrapAdmins.of(java.util.List.of("founder@example.com"));

        SetUserRoles.Result result =
                new SetUserRoles(users, declared).execute(THE_ADMIN, Set.of(Role.USER));

        assertThat(result.status())
                .as("an admin by configuration is an admin — refusing here would be refusing a"
                        + " change that leaves the system perfectly administrable")
                .isEqualTo(SetUserRoles.Status.UPDATED);
        Mockito.verify(users).setRoles(THE_ADMIN, Set.of(Role.USER));
    }

    @Test
    @DisplayName("one admin of several may be demoted")
    void a_second_admin_keeps_the_door_open() {
        UserRepository users = usersWhere(THE_ADMIN, Set.of(Role.USER, Role.ADMIN), 2);

        SetUserRoles.Result result =
                new SetUserRoles(users, BootstrapAdmins.none()).execute(THE_ADMIN, Set.of(Role.USER));

        assertThat(result.status()).isEqualTo(SetUserRoles.Status.UPDATED);
    }

    @Test
    @DisplayName("a change that does not touch ADMIN is never the last-admin case")
    void granting_is_not_removing() {
        UserRepository users = usersWhere(THE_ADMIN, Set.of(Role.USER, Role.ADMIN), 1);

        SetUserRoles.Result result = new SetUserRoles(users, BootstrapAdmins.none())
                .execute(THE_ADMIN, Set.of(Role.USER, Role.ADMIN, Role.MODERATOR));

        assertThat(result.status())
                .as("the sole admin adding MODERATOR to themselves is not a lockout")
                .isEqualTo(SetUserRoles.Status.UPDATED);
        Mockito.verify(users, Mockito.never()).countAdmins();
    }

    private static UserRepository usersWhere(Email email, Set<Role> roles, int admins) {
        UserRepository users = Mockito.mock(UserRepository.class);
        User user = new User(UUID.randomUUID(), email, new HashedPassword("argon2"), null, roles);
        Mockito.when(users.findBy(email)).thenReturn(Optional.of(user));
        Mockito.when(users.countAdmins()).thenReturn(admins);
        return users;
    }
}

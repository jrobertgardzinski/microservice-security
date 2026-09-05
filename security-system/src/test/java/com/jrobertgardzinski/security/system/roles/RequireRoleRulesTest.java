package com.jrobertgardzinski.security.system.roles;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.vo.Role;
import com.jrobertgardzinski.util.constraint.Outcome;
import io.qameta.allure.Allure;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Example;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@Epic("Security")
@Feature("Roles")
@Story("Requiring a role: the store's grant or the deployment's bootstrap list, refused by code otherwise")
class RequireRoleRulesTest {

    private static final Email ADMIN = Email.of("admin@example.com");
    private static final Email MEMBER = Email.of("member@example.com");
    private static final Email STRANGER = Email.of("stranger@example.com");

    private static RolesOf store(Map<Email, Set<Role>> granted) {
        return email -> granted.getOrDefault(email, Set.of());
    }

    @Example
    @Label("a role the store granted satisfies the requirement")
    void grantedRoleSatisfies() {
        RequireRole require = new RequireRole(BootstrapAdmins.none(), store(Map.of(ADMIN, Set.of(Role.USER, Role.ADMIN))));
        Outcome<Set<Role>> outcome = require.check(ADMIN, Role.ADMIN);
        assertThat(outcome).isInstanceOf(Outcome.Allowed.class);
        assertThat(outcome.findValue()).contains(Set.of(Role.USER, Role.ADMIN));
    }

    @Example
    @Label("a bootstrap admin is ADMIN before any grant exists")
    void bootstrapAdminIsAdmin() {
        RequireRole require = new RequireRole(BootstrapAdmins.of(List.of(" Admin@Example.com ")), store(Map.of()));
        assertThat(require.check(ADMIN, Role.ADMIN)).isInstanceOf(Outcome.Allowed.class);
        assertThat(require.rolesInForce(ADMIN)).contains(Role.ADMIN);
    }

    @Example
    @Label("a USER without the role is refused with NOT_AN_ADMIN")
    void memberIsRefused() {
        RequireRole require = new RequireRole(BootstrapAdmins.none(), store(Map.of(MEMBER, Set.of(Role.USER))));
        Outcome<Set<Role>> outcome = require.check(MEMBER, Role.ADMIN);
        assertThat(outcome).isInstanceOf(Outcome.Rejected.class);
        assertThat(outcome.errorCodes()).containsExactly("NOT_AN_ADMIN");
    }

    @Example
    @Label("an identity the store does not know has no roles at all")
    void strangerHasNoRoles() {
        RequireRole require = new RequireRole(BootstrapAdmins.none(), store(Map.of()));
        assertThat(require.rolesInForce(STRANGER)).isEmpty();
        assertThat(require.check(STRANGER, Role.USER).errorCodes()).containsExactly("NOT_A_USER");
    }

    @Property
    @Label("every role has a refusal code of its own, and it names the missing role")
    void everyRoleHasItsOwnCode(@ForAll("roles") Role required) {
        Allure.parameter("required", required);
        RequireRole require = new RequireRole(BootstrapAdmins.none(), store(Map.of()));
        List<String> codes = require.check(STRANGER, required).errorCodes();
        assertThat(codes).hasSize(1);
        assertThat(codes.getFirst()).startsWith("NOT_A").endsWith(required.name());
    }

    @Provide
    Arbitrary<Role> roles() {
        return Arbitraries.of(Role.class);
    }

    @Example
    @Label("a blank bootstrap entry is ignored, an invalid one is refused at declaration")
    void bootstrapListIsParsedNotTrusted() {
        assertThat(BootstrapAdmins.of(List.of("", "  ")).admins()).isEmpty();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> BootstrapAdmins.of(List.of("not-an-address")))
                .isInstanceOf(RuntimeException.class);
    }
}

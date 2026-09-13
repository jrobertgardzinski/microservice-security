package com.jrobertgardzinski;

import io.micronaut.context.ApplicationContext;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.ServerFilter;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A law, not an example: every controller in this service is either behind {@link
 * AuthorizationFilter} or on the list below of doors that are open ON PURPOSE.
 *
 * <p>The filter's reach is a list of path patterns written once, by hand, on an annotation. Nothing
 * tied it to the set of controllers, so a new controller was guarded only if whoever added it
 * remembered — and forgetting has no symptom: the endpoint works, for everybody, and the tests that
 * exercise it pass because they send a token anyway. That is the one class of mistake a test suite
 * built from examples cannot see.
 *
 * <p>Adding a controller therefore forces a decision here: name it public and say why, or put its
 * prefix under the filter. The list is short and each line is a sentence somebody has to be willing
 * to write.
 */
@Epic("Authorization")
@Feature("Filter coverage")
class GuardedPathsTest {

    /**
     * Doors that are open on purpose — each one reachable before there is anybody to authenticate,
     * or authenticated by something other than a bearer token.
     */
    private static final Set<String> DELIBERATELY_PUBLIC = Set.of(
            "/register",              // there is no account yet
            "/authenticate",          // this is where a token comes from
            "/authenticate/factor",   // the rest of the sign-in chain; the ticket is the credential
            "/verify-email",          // the link in the mail is the credential
            "/reset-password",        // likewise, and reached by somebody who cannot sign in
            "/confirm-email-change",  // likewise
            "/refresh",               // the refresh COOKIE is the credential, not a bearer token
            "/logout",                // same cookie, and a logout must never need a live token
            "/oauth",                 // the provider's dance; the state cookie carries the session
            "/.well-known",           // the JWK set is public by definition
            "/test/mailbox",          // test environment only (@Requires(env = "test"))
            "/test/clock");           // the steerable clock, likewise test-only (shared library)

    static ApplicationContext context;

    @BeforeAll
    static void start() {
        context = ApplicationContext.run("test");
    }

    @AfterAll
    static void stop() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    @DisplayName("every controller is either filtered or listed as public")
    void no_controller_is_guarded_only_by_memory() {
        List<String> patterns = List.of(AuthorizationFilter.class.getAnnotation(ServerFilter.class).value());

        List<String> unguarded = context.getBeanDefinitions(Object.class).stream()
                .filter(definition -> definition.hasAnnotation(Controller.class))
                .map(definition -> definition.stringValue(Controller.class).orElse(""))
                .filter(path -> !path.isBlank())
                .distinct()
                .filter(path -> !DELIBERATELY_PUBLIC.contains(path))
                .filter(path -> patterns.stream().noneMatch(pattern -> covers(pattern, path)))
                .sorted()
                .toList();

        assertThat(unguarded)
                .as("these controllers answer anybody who asks. Either add the prefix to"
                        + " AuthorizationFilter's @ServerFilter, or list the path in"
                        + " DELIBERATELY_PUBLIC with the reason it is open")
                .isEmpty();
    }

    @Test
    @DisplayName("the public list names only paths that exist")
    void the_public_list_does_not_rot() {
        Set<String> declared = context.getBeanDefinitions(Object.class).stream()
                .filter(definition -> definition.hasAnnotation(Controller.class))
                .map(definition -> definition.stringValue(Controller.class).orElse(""))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());

        assertThat(DELIBERATELY_PUBLIC)
                .as("a line here for a controller that no longer exists is a permission nobody"
                        + " granted, waiting for a path of that name to come back")
                .allSatisfy(path -> assertThat(declared).contains(path));
    }

    /** {@code /account/**} covers {@code /account/email}; {@code /me} covers only {@code /me}. */
    private static boolean covers(String pattern, String controllerPath) {
        if (pattern.endsWith("/**")) {
            String prefix = pattern.substring(0, pattern.length() - 3);
            return controllerPath.equals(prefix) || controllerPath.startsWith(prefix + "/");
        }
        return pattern.equals(controllerPath);
    }
}

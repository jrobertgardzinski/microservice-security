package com.jrobertgardzinski;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Refuses a production-path start without a declared deployment profile. The bare application.yml
 * holds only the service's INVARIANTS (what the service is); everything environment-shaped lives
 * in the profile files (where it stands) — so a start that names no profile is a start nobody
 * decided, and the most common way it happens is a FORGOTTEN profile, which is exactly when
 * strictness matters (default-deny).
 *
 * <p>Called at the top of {@code main} and ONLY there — wiring, not bean-graph luck: an
 * undeclared context today dies on a confusing injection error (the offboarding listener is
 * active outside {@code test} while its JDBC dependency needs a DataSource) long before any
 * eager bean could speak, so the refusal must happen before the context exists at all. Tests
 * boot their contexts directly and never enter main; Micronaut hands them the deduced
 * {@code test} environment instead.
 *
 * <p>The law counts only the deployment trio {dev, test, prod}: extras like {@code k8s} ride
 * along freely. Zero trio members = nobody decided; {@code dev} and {@code prod} together = two
 * people decided differently, which is worse.
 */
final class ProfileGuard {

    private static final Set<String> DEPLOYMENT_PROFILES = Set.of("dev", "test", "prod");

    private ProfileGuard() {
    }

    /** @param rawEnvironments the comma-separated declaration, e.g. the MICRONAUT_ENVIRONMENTS value */
    static void requireDeclaredProfile(String rawEnvironments) {
        List<String> names = rawEnvironments == null || rawEnvironments.isBlank()
                ? List.of()
                : Arrays.stream(rawEnvironments.split(",")).map(String::trim).toList();
        List<String> declared = names.stream().filter(DEPLOYMENT_PROFILES::contains).toList();
        if (declared.isEmpty())
            throw new IllegalStateException("no deployment profile declared (found: " + names
                    + ") - start with MICRONAUT_ENVIRONMENTS=dev or MICRONAUT_ENVIRONMENTS=prod;"
                    + " a start nobody decided must not happen");
        if (declared.contains("dev") && declared.contains("prod"))
            throw new IllegalStateException("both 'dev' and 'prod' are declared (" + names
                    + ") - an ambiguous start is worse than a refused one");
    }
}

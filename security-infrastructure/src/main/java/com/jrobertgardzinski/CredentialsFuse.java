package com.jrobertgardzinski;

import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;

import java.util.Set;

/**
 * Outside dev/test the datasource credentials MUST arrive with the deployment — properties or
 * environment variables — and must not be a known dev default. This is the profile-dependent
 * kind of key made executable: in dev the ladder-with-default is a convenience (postgres on the
 * bundled compose is no secret), everywhere else a default is a lie — either a real secret in
 * the repository or a "working" value that quietly points the identity service at the wrong
 * database instead of refusing to start.
 *
 * <p>Wiring, not sniffing: this bean EXISTS only under a DECLARED {@code prod} profile — under
 * the ProfileGuard's law (exactly the trio, no ambiguity) that is the same set as "outside
 * dev/test", and binding to the declared name keeps the boot deterministic: on an undeclared
 * start the guard speaks alone. It also fires when
 * no datasource is configured at all — an identity service without persistence outside dev/test
 * is a fleet that forgets every account on restart.
 */
@Context
@Requires(env = "prod")
public class CredentialsFuse {

    /** The bundled-compose credentials from application-dev.yml — fine in dev, a lie anywhere else. */
    private static final Set<String> KNOWN_DEV_DEFAULTS = Set.of("postgres", "secret");

    CredentialsFuse(Environment environment) {
        String password;
        try {
            password = environment.getProperty("datasources.default.password", String.class)
                    .orElse(null);
        } catch (RuntimeException unresolvedPlaceholder) {
            // application-prod.yml says ${DB_PASSWORD} with no default; when the variable is
            // missing the resolver throws — for this fuse that IS "not set", and the operator
            // deserves the message that names the variable, not a placeholder stack trace
            password = null;
        }
        if (password == null || password.isBlank())
            throw new IllegalStateException("outside dev/test the identity service requires an"
                    + " explicit datasource password - set DB_PASSWORD (or"
                    + " datasources.default.password) in the deployment; there is deliberately"
                    + " no default");
        if (KNOWN_DEV_DEFAULTS.contains(password))
            throw new IllegalStateException("the datasource password is a known dev default -"
                    + " outside dev/test set a real DB_PASSWORD; refusing to point the identity"
                    + " service at a database that happens to answer");
    }
}

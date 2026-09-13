package com.jrobertgardzinski;

import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;

/**
 * Outside dev/test the JWT signing pair MUST arrive with the deployment.
 *
 * <p>With no keys configured, {@link JwtAccessTokenMint} generates a pair at startup. In dev and in
 * tests that is exactly right — nothing outside the process verifies those tokens, and a restart
 * invalidating them fails towards safety. In a deployment it is a promise quietly broken: the whole
 * point of the self-contained token is that other services verify it OFFLINE against
 * {@code /.well-known/jwks.json}, and an ephemeral pair means the key changes on every restart and
 * differs between replicas. Every consumer then sees valid tokens it cannot verify, intermittently,
 * with nothing in any log to explain it — and the fallback is to call introspection for every
 * request, which is the load the design exists to avoid.
 *
 * <p>Same shape as {@link CredentialsFuse}, and for the same reason: this is the profile-dependent
 * kind of key, so the profile is where it is answered. The bean exists only under a DECLARED
 * {@code prod} profile, so the boot stays deterministic and the ProfileGuard speaks alone on an
 * undeclared start.
 */
@Context
@Requires(env = "prod")
public class JwtKeyFuse {

    JwtKeyFuse(@Value("${security.jwt.private-key:}") String privateKeyBase64,
               @Value("${security.jwt.public-key:}") String publicKeyBase64) {
        if (privateKeyBase64.isBlank() || publicKeyBase64.isBlank()) {
            throw new IllegalStateException("outside dev/test the identity service requires an"
                    + " explicit JWT signing pair - set security.jwt.private-key and"
                    + " security.jwt.public-key (base64 PKCS#8 / X.509 DER). Without them a pair is"
                    + " generated at startup, which changes on every restart and differs between"
                    + " replicas: offline verification against the JWK set would fail for reasons"
                    + " nothing logs.");
        }
    }
}

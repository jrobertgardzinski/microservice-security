package com.jrobertgardzinski;

import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;

/**
 * Outside dev/test the metrics endpoint must not be open to whoever can reach the service.
 *
 * <p>There is no separate management port here — Micronaut serves {@code /prometheus} on the same
 * connector as the API — so the only thing between a public deployment's metrics and the internet
 * is whatever the deployment puts there. A token is the cheapest such thing, and this refuses the
 * boot without one rather than letting the endpoint quietly stay open, which is how it stayed open
 * for as long as it did.
 *
 * <p>Same shape as {@link CredentialsFuse} and {@link JwtKeyFuse}: the bean exists only under a
 * DECLARED {@code prod} profile.
 */
@Context
@Requires(env = "prod")
public class MetricsTokenFuse {

    MetricsTokenFuse(@Value("${security.metrics.token:}") String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("outside dev/test the metrics endpoint needs a token -"
                    + " set security.metrics.token (or SECURITY_METRICS_TOKEN) in the deployment and"
                    + " give the same value to the scraper (Prometheus: authorization.credentials)."
                    + " /prometheus is served on the API's own connector, so without one it answers"
                    + " anybody who can reach this service; /health stays open either way, because a"
                    + " probe that needs credentials fails during the incidents it exists for");
        }
    }
}

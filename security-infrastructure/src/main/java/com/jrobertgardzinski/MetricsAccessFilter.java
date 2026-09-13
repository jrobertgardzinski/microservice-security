package com.jrobertgardzinski;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Guards {@code /prometheus} with a token the scraper carries.
 *
 * <p>Micronaut serves the metrics on the same connector as the API — there is no separate
 * management port — so an endpoint meant for one scraper inside a network was answering anybody who
 * could reach the service. What it gives away is not dramatic and is not nothing: request timings
 * per route (a login endpoint's latency profile is a hint about what is being tried), pool sizes and
 * saturation, JVM vitals, and a live view of whether an attack is working.
 *
 * <p>{@code /health} deliberately stays open. It answers {@code {"status":"UP"}} and nothing else,
 * and a probe that needs credentials is a probe that fails during exactly the incidents it exists
 * for.
 *
 * <p>This filter exists only when {@code security.metrics.token} is set, so the dev stack — whose
 * Prometheus scrapes over a private compose network — keeps working with nothing to configure.
 * Under a declared {@code prod} profile the token is REQUIRED ({@link MetricsTokenFuse}), which is
 * where "the same connector as the API" stops being a detail.
 */
@ServerFilter({"/prometheus", "/prometheus/**"})
@Requires(property = "security.metrics.token")
final class MetricsAccessFilter {

    private final byte[] expected;

    MetricsAccessFilter(@Value("${security.metrics.token}") String token) {
        this.expected = token.strip().getBytes(StandardCharsets.UTF_8);
    }

    @RequestFilter
    @Nullable
    HttpResponse<?> guard(HttpRequest<?> request) {
        String presented = Caller.bearerToken(request);
        if (presented == null
                // constant time: this is a fixed secret compared on every scrape, so a timing
                // difference here is measurable in a way a per-user one is not
                || !MessageDigest.isEqual(presented.getBytes(StandardCharsets.UTF_8), expected)) {
            return HttpResponse.unauthorized().body(Refusal.of("NOT_AUTHENTICATED"));
        }
        return null;   // proceed to the endpoint
    }
}

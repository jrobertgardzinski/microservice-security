package com.jrobertgardzinski;

import com.jrobertgardzinski.security.domain.vo.IpAddress;
import io.micronaut.context.annotation.Value;
import io.micronaut.http.HttpRequest;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Resolves the {@link IpAddress} that brute-force protection treats as the request's source.
 *
 * <p>The source is the brute-force key, so it must be hard to spoof: {@code X-Forwarded-For} is
 * honoured <em>only</em> when the request actually arrived from a configured trusted proxy
 * (load balancer / reverse proxy); otherwise the connection's remote address is used. This stops a
 * client from forging an arbitrary source — and thus dodging its own lockout — by sending an
 * {@code X-Forwarded-For} header directly. Trusted proxies are configured via
 * {@code security.trusted-proxies} (a comma-separated list; empty by default).
 */
@Singleton
public class ClientIpResolver {

    private final Set<String> trustedProxies;

    public ClientIpResolver(@Value("${security.trusted-proxies:}") List<String> trustedProxies) {
        this.trustedProxies = trustedProxies == null ? Set.of()
                : trustedProxies.stream()
                        .filter(proxy -> !proxy.isBlank())
                        .map(String::trim)
                        .collect(Collectors.toUnmodifiableSet());
    }

    public IpAddress resolve(HttpRequest<?> request) {
        String remoteAddress = request.getRemoteAddress().getAddress().getHostAddress();
        if (trustedProxies.contains(remoteAddress)) {
            String forwardedFor = request.getHeaders().get("X-Forwarded-For");
            if (forwardedFor != null && !forwardedFor.isBlank()) {
                return new IpAddress(forwardedFor.split(",")[0].trim());
            }
        }
        return new IpAddress(remoteAddress);
    }
}

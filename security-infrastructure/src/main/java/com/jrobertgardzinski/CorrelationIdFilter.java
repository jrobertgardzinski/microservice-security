package com.jrobertgardzinski;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.UUID;

/**
 * Correlation id for tracing a request across services. Reads the inbound {@code X-Correlation-Id}
 * (or mints one) so a call arriving from another service (a gate checking {@code /me}) keeps the
 * same id; puts it in the logging context and logs one access line, then echoes it on the response.
 * Grep the id and this service's part of a cross-service journey shows up next to everyone else's.
 */
@ServerFilter("/**")
final class CorrelationIdFilter {

    static final String HEADER = "X-Correlation-Id";
    private static final String ATTR = "cid";
    private static final Logger log = LoggerFactory.getLogger(CorrelationIdFilter.class);

    @RequestFilter
    void onRequest(HttpRequest<?> request) {
        String cid = request.getHeaders().get(HEADER);
        if (cid == null || cid.isBlank()) {
            cid = UUID.randomUUID().toString().substring(0, 8);
        }
        request.setAttribute(ATTR, cid);
        MDC.put(ATTR, cid);
        log.info("cid={} {} {}", cid, request.getMethod(), request.getPath());
    }

    @ResponseFilter
    void onResponse(HttpRequest<?> request, MutableHttpResponse<?> response) {
        request.getAttribute(ATTR, String.class).ifPresent(cid -> response.header(HEADER, cid));
        MDC.remove(ATTR);
    }
}

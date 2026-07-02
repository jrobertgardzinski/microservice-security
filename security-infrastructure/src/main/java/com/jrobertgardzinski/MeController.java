package com.jrobertgardzinski;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.http.annotation.Get;

import java.util.Map;

/**
 * A protected resource: returns who the caller is. Reaching this controller means
 * {@link AuthorizationFilter} already authorized the access token and published the email, so here
 * we just read it back.
 */
// controllers do blocking work (JDBC, the mail service's HTTP client) — keep it off the event loop
@ExecuteOn(TaskExecutors.BLOCKING)
@Controller("/me")
final class MeController {

    @Get(produces = MediaType.APPLICATION_JSON)
    HttpResponse<Map<String, Object>> me(HttpRequest<?> request) {
        String email = request.getAttribute(AuthorizationFilter.AUTHENTICATED_EMAIL, String.class).orElseThrow();
        return HttpResponse.ok(Map.of("email", email));
    }
}

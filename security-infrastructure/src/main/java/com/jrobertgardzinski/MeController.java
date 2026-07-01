package com.jrobertgardzinski;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;

import java.util.Map;

/**
 * A protected resource: returns who the caller is. Reaching this controller means
 * {@link AuthorizationFilter} already authorized the access token and published the email, so here
 * we just read it back.
 */
@Controller("/me")
final class MeController {

    @Get(produces = MediaType.APPLICATION_JSON)
    HttpResponse<Map<String, Object>> me(HttpRequest<?> request) {
        String email = request.getAttribute(AuthorizationFilter.AUTHENTICATED_EMAIL, String.class).orElseThrow();
        return HttpResponse.ok(Map.of("email", email));
    }
}

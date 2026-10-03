package com.jrobertgardzinski;

import com.jrobertgardzinski.security.application.identity.IdentityService;
import com.jrobertgardzinski.security.domain.vo.Role;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;

import java.util.Map;

/**
 * A protected resource: returns who the caller is AND what roles they hold — the source of truth
 * other services read to gate moderator/admin actions. Reaching this controller means
 * {@link AuthorizationFilter} already authorized the access token and published the email; here we
 * read it back and look up the user's roles.
 */
// controllers do blocking work (JDBC, the mail service's HTTP client) — keep it off the event loop
@ExecuteOn(TaskExecutors.BLOCKING)
@Controller("/me")
final class MeController {

    private final IdentityService identity;

    MeController(IdentityService identity) {
        this.identity = identity;
    }

    @Get(produces = MediaType.APPLICATION_JSON)
    HttpResponse<Map<String, Object>> me(HttpRequest<?> request) {
        IdentityService.Profile profile = identity.me(Caller.of(request));
        return HttpResponse.ok(Map.of(
                "id", profile.id().toString(),
                "email", profile.email().value(),
                "roles", profile.roles().stream().map(Role::name).sorted().toList(),
                "mfaCompliant", profile.mfaCompliant(),
                "requiredFactors", profile.requiredFactors(),
                "haveFactors", profile.haveFactors()));
    }
}

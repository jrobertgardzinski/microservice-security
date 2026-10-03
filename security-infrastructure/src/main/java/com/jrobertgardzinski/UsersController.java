package com.jrobertgardzinski;

import com.jrobertgardzinski.security.application.identity.IdentityService;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The names behind a batch of user ids — what a gallery or a thread shows next to content it
 * holds by id. Anonymous, like the content itself; never the address, only its display form; an
 * id nobody holds is left out, so a deleted account and an invented id look the same.
 */
@ExecuteOn(TaskExecutors.BLOCKING)
@Controller("/users")
final class UsersController {

    private final IdentityService identity;
    private final ClientIpResolver ipResolver;

    UsersController(IdentityService identity, ClientIpResolver ipResolver) {
        this.identity = identity;
        this.ipResolver = ipResolver;
    }

    @Get(produces = MediaType.APPLICATION_JSON)
    HttpResponse<?> names(HttpRequest<?> request, @QueryValue(defaultValue = "") String ids) {
        return switch (identity.displayNames(ids, ipResolver.resolve(request))) {
            case IdentityService.Names.Found found -> HttpResponse.ok(found.names().stream()
                    .map(named -> {
                        Map<String, String> entry = new LinkedHashMap<>();
                        entry.put("id", named.id().toString());
                        entry.put("displayName", named.displayName().value());
                        return entry;
                    })
                    .toList());
            case IdentityService.Names.InvalidId invalid -> HttpResponse.badRequest(Map.of("status", "INVALID_ID"));
            case IdentityService.Names.TooManyIds tooMany ->
                    HttpResponse.badRequest(Map.of("status", "TOO_MANY_IDS", "max", tooMany.max()));
            case IdentityService.Names.Throttled throttled ->
                    HttpResponse.status(HttpStatus.TOO_MANY_REQUESTS)
                            .header("Retry-After", String.valueOf(throttled.retryAfterSeconds()))
                            .body(Refusal.alsoAsError("TOO_MANY_ATTEMPTS"));
        };
    }
}

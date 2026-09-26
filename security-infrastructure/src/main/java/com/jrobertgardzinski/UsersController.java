package com.jrobertgardzinski;

import com.jrobertgardzinski.identity.UserId;
import com.jrobertgardzinski.security.domain.vo.DisplayName;
import com.jrobertgardzinski.security.system.identity.DisplayNames;
import com.jrobertgardzinski.security.system.throttle.SourceThrottle;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import jakarta.inject.Named;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * The names behind a batch of user ids — what a gallery or a thread shows next to content it
 * holds by id. Anonymous, like the content itself; never the address, only its display form; an
 * id nobody holds is left out, so a deleted account and an invented id look the same.
 */
@ExecuteOn(TaskExecutors.BLOCKING)
@Controller("/users")
final class UsersController {

    static final int MAX_IDS = 100;

    private final DisplayNames displayNames;
    private final SourceThrottle throttle;
    private final ClientIpResolver ipResolver;

    UsersController(DisplayNames displayNames, @Named("display-names") SourceThrottle throttle,
                    ClientIpResolver ipResolver) {
        this.displayNames = displayNames;
        this.throttle = throttle;
        this.ipResolver = ipResolver;
    }

    @Get(produces = MediaType.APPLICATION_JSON)
    HttpResponse<?> names(HttpRequest<?> request, @QueryValue(defaultValue = "") String ids) {
        SourceThrottle.Decision decision = throttle.check(ipResolver.resolve(request));
        if (!decision.allowed()) {
            return HttpResponse.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header("Retry-After", String.valueOf(decision.retryAfterSeconds()))
                    .body(Refusal.alsoAsError("TOO_MANY_ATTEMPTS"));
        }
        List<UserId> asked;
        try {
            asked = Arrays.stream(ids.split(",")).map(String::trim).filter(id -> !id.isEmpty())
                    .map(UserId::of).distinct().toList();
        } catch (IllegalArgumentException notAnId) {
            return HttpResponse.badRequest(Map.of("status", "INVALID_ID"));
        }
        if (asked.size() > MAX_IDS) {
            return HttpResponse.badRequest(Map.of("status", "TOO_MANY_IDS", "max", MAX_IDS));
        }
        Map<UserId, DisplayName> names = displayNames.of(asked);
        return HttpResponse.ok(asked.stream().filter(names::containsKey)
                .map(id -> {
                    Map<String, String> entry = new java.util.LinkedHashMap<>();
                    entry.put("id", id.toString());
                    entry.put("displayName", names.get(id).value());
                    return entry;
                })
                .toList());
    }
}

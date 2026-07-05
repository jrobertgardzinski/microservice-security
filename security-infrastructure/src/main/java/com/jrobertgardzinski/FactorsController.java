package com.jrobertgardzinski;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.repository.EnrolledFactorRepository;
import com.jrobertgardzinski.security.domain.vo.FactorType;
import com.jrobertgardzinski.security.system.mfa.EnrolFactor;
import com.jrobertgardzinski.security.system.mfa.FactorRegistry;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;

import java.util.List;
import java.util.Map;

/**
 * Manage a signed-in user's MFA factors — the per-user half of the configuration. Enrolling proves
 * control of the new factor in two steps ({@code enroll/start} sends a challenge, {@code
 * enroll/confirm} accepts one proof). {@link AuthorizationFilter} has already authorized the caller
 * and published their e-mail; every action is against their own factors.
 */
@ExecuteOn(TaskExecutors.BLOCKING)
@Controller("/account/factors")
final class FactorsController {

    private final EnrolFactor enrolFactor;
    private final EnrolledFactorRepository enrolledFactors;
    private final FactorRegistry registry;
    private final TransactionBoundary transactionBoundary;

    FactorsController(EnrolFactor enrolFactor, EnrolledFactorRepository enrolledFactors,
                      FactorRegistry registry, TransactionBoundary transactionBoundary) {
        this.enrolFactor = enrolFactor;
        this.enrolledFactors = enrolledFactors;
        this.registry = registry;
        this.transactionBoundary = transactionBoundary;
    }

    @Get(produces = MediaType.APPLICATION_JSON)
    HttpResponse<Map<String, Object>> list(HttpRequest<?> request) {
        Email caller = caller(request);
        List<Map<String, String>> have = enrolledFactors.findByUser(caller).stream()
                .map(f -> Map.of("type", f.type().value(), "label", f.label()))
                .toList();
        List<String> offered = registry.offered().stream().map(FactorType::value).sorted().toList();
        return HttpResponse.ok(Map.of("have", have, "offered", offered));
    }

    @Post(value = "/{type}/enroll/start", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
    HttpResponse<Map<String, Object>> start(HttpRequest<?> request, @PathVariable String type,
                                            @Nullable @Body Map<String, String> body) {
        Email caller = caller(request);
        // for the e-mail factor the target defaults to the caller's own (already verified) address
        String target = body != null && body.get("target") != null ? body.get("target") : caller.value();
        return respond(transactionBoundary.execute(
                () -> enrolFactor.start(caller, FactorType.of(type), target)));
    }

    @Post(value = "/{type}/enroll/confirm", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
    HttpResponse<Map<String, Object>> confirm(HttpRequest<?> request, @PathVariable String type,
                                              @Body Map<String, String> body) {
        Email caller = caller(request);
        return respond(transactionBoundary.execute(
                () -> enrolFactor.confirm(caller, FactorType.of(type), body.get("code"))));
    }

    @Delete(value = "/{type}", produces = MediaType.APPLICATION_JSON)
    HttpResponse<Map<String, Object>> remove(HttpRequest<?> request, @PathVariable String type) {
        Email caller = caller(request);
        transactionBoundary.execute(() -> {
            enrolledFactors.remove(caller, FactorType.of(type));
            return null;
        });
        return HttpResponse.ok(Map.of("status", "REMOVED"));
    }

    private static HttpResponse<Map<String, Object>> respond(EnrolFactor.Result result) {
        return switch (result) {
            case EnrolFactor.Result.Started started -> {
                // a code factor sent a code (no display); a possession factor returns what to show (TOTP URI)
                Map<String, Object> body = started.display() == null
                        ? Map.of("status", "ENROLL_CODE_SENT")
                        : Map.of("status", "ENROLL_SETUP", "display", started.display());
                yield HttpResponse.accepted().body(body);
            }
            case EnrolFactor.Result.Enrolled enrolled ->
                    HttpResponse.ok(Map.of("status", "ENROLLED", "type", enrolled.type().value()));
            case EnrolFactor.Result.WrongProof wrong ->
                    HttpResponse.<Map<String, Object>>status(HttpStatus.UNAUTHORIZED)
                            .body(Map.of("status", "WRONG_CODE"));
            case EnrolFactor.Result.NoPendingEnrolment none ->
                    HttpResponse.<Map<String, Object>>badRequest(Map.of("status", "NO_PENDING_ENROLMENT"));
            case EnrolFactor.Result.UnsupportedFactor unsupported ->
                    HttpResponse.<Map<String, Object>>badRequest(Map.of("status", "UNSUPPORTED_FACTOR"));
        };
    }

    private static Email caller(HttpRequest<?> request) {
        return Email.of(request.getAttribute(AuthorizationFilter.AUTHENTICATED_EMAIL, String.class).orElseThrow());
    }
}

package com.jrobertgardzinski;

import com.jrobertgardzinski.security.application.mfa.MfaService;
import com.jrobertgardzinski.security.domain.vo.StepUpAction;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;

import java.util.Map;

/**
 * A signed-in user's recovery codes: {@code POST} mints a fresh batch — the plain codes appear in
 * this one response and never again (only hashes are stored); any previous batch dies with it.
 * {@code GET} says how many remain unspent. {@link AuthorizationFilter} has already authorized
 * the caller; the codes are always their own.
 */
@ExecuteOn(TaskExecutors.BLOCKING)
@Controller("/account/recovery-codes")
final class RecoveryCodesController {

    private final MfaService mfa;
    private final StepUpGuard stepUpGuard;

    RecoveryCodesController(MfaService mfa, StepUpGuard stepUpGuard) {
        this.mfa = mfa;
        this.stepUpGuard = stepUpGuard;
    }

    @Post(produces = MediaType.APPLICATION_JSON)
    HttpResponse<Map<String, Object>> generate(HttpRequest<?> request) {
        return switch (mfa.generateRecoveryCodes(Caller.of(request),
                () -> stepUpGuard.requireElevation(request, StepUpAction.GENERATE_RECOVERY_CODES).isEmpty())) {
            case MfaService.RecoveryCodes.Generated generated ->
                    HttpResponse.ok(Map.of("status", "GENERATED", "codes", generated.codes()));
            case MfaService.RecoveryCodes.StepUpRequired stepUp ->
                    StepUpGuard.refusal(StepUpAction.GENERATE_RECOVERY_CODES);
        };
    }

    @Get(produces = MediaType.APPLICATION_JSON)
    HttpResponse<Map<String, Object>> remaining(HttpRequest<?> request) {
        return HttpResponse.ok(Map.of("unused", mfa.unusedRecoveryCodes(Caller.of(request))));
    }
}

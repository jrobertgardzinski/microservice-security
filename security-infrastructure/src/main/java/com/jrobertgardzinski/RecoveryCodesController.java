package com.jrobertgardzinski;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.repository.RecoveryCodeRepository;
import com.jrobertgardzinski.security.system.mfa.GenerateRecoveryCodes;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;

import java.util.List;
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

    private final GenerateRecoveryCodes generateRecoveryCodes;
    private final RecoveryCodeRepository recoveryCodes;
    private final TransactionBoundary transactionBoundary;

    RecoveryCodesController(GenerateRecoveryCodes generateRecoveryCodes,
                            RecoveryCodeRepository recoveryCodes,
                            TransactionBoundary transactionBoundary) {
        this.generateRecoveryCodes = generateRecoveryCodes;
        this.recoveryCodes = recoveryCodes;
        this.transactionBoundary = transactionBoundary;
    }

    @Post(produces = MediaType.APPLICATION_JSON)
    HttpResponse<Map<String, Object>> generate(HttpRequest<?> request) {
        Email caller = caller(request);
        List<String> plainCodes = transactionBoundary.execute(() -> generateRecoveryCodes.execute(caller));
        return HttpResponse.ok(Map.of("status", "GENERATED", "codes", plainCodes));
    }

    @Get(produces = MediaType.APPLICATION_JSON)
    HttpResponse<Map<String, Object>> remaining(HttpRequest<?> request) {
        return HttpResponse.ok(Map.of("unused", recoveryCodes.unusedCount(caller(request))));
    }

    private static Email caller(HttpRequest<?> request) {
        return Email.of(request.getAttribute(AuthorizationFilter.AUTHENTICATED_EMAIL, String.class).orElseThrow());
    }
}

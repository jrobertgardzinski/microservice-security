package com.jrobertgardzinski;

import com.jrobertgardzinski.security.domain.vo.token.VerificationToken;
import com.jrobertgardzinski.security.system.account.ConfirmEmailChange;
import com.jrobertgardzinski.security.system.account.ConfirmEmailChangeResult;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;

import java.util.Map;

/**
 * Public HTTP entry point to confirm an email change with the token from the link (the recipient of
 * the link is not signed in yet). Drives the {@link ConfirmEmailChange} use case.
 */
@Controller("/confirm-email-change")
final class ConfirmEmailChangeController {

    private final ConfirmEmailChange confirmEmailChange;
    private final TransactionBoundary transactionBoundary;

    ConfirmEmailChangeController(ConfirmEmailChange confirmEmailChange, TransactionBoundary transactionBoundary) {
        this.confirmEmailChange = confirmEmailChange;
        this.transactionBoundary = transactionBoundary;
    }

    @Post(consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
    HttpResponse<?> confirm(@Body Map<String, Object> body) {
        VerificationToken token = new VerificationToken((String) body.get("token"));
        ConfirmEmailChangeResult result = transactionBoundary.execute(() -> confirmEmailChange.execute(token));
        return switch (result) {
            case ConfirmEmailChangeResult.EmailChanged changed ->
                    HttpResponse.ok(Map.of("status", "EMAIL_CHANGED", "email", changed.newEmail().value()));
            case ConfirmEmailChangeResult.InvalidToken ignored ->
                    HttpResponse.badRequest().body(Map.of("status", "INVALID_TOKEN"));
        };
    }
}

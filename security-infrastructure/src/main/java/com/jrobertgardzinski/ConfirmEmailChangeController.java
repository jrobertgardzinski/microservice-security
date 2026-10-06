package com.jrobertgardzinski;

import com.jrobertgardzinski.security.application.mailbox.EmailChangeService;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;

import java.util.Map;

/**
 * Public HTTP entry point to confirm an email change with the token from the link (the recipient of
 * the link is not signed in yet). Goes through
 * {@link com.jrobertgardzinski.security.application.account.AccountService}.
 */
// controllers do blocking work (JDBC, the mail service's HTTP client) — keep it off the event loop
@ExecuteOn(TaskExecutors.BLOCKING)
@Controller("/confirm-email-change")
final class ConfirmEmailChangeController {

    private final EmailChangeService emailChange;

    ConfirmEmailChangeController(EmailChangeService emailChange) {
        this.emailChange = emailChange;
    }

    @Post(consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
    HttpResponse<?> confirm(@Body Map<String, Object> body) {
        return switch (emailChange.confirmEmailChange(JsonBody.text(body, "token"))) {
            case EmailChangeService.EmailConfirmation.EmailChanged changed ->
                    HttpResponse.ok(Map.of("status", "EMAIL_CHANGED", "email", changed.newEmail().value()));
            // 409: the request was well formed and the token was good — the world moved. Its own
            // status, because "invalid token" would send the owner to fetch another one that fails
            // exactly the same way.
            case EmailChangeService.EmailConfirmation.EmailTaken taken ->
                    HttpResponse.status(HttpStatus.CONFLICT).body(Map.of("status", "EMAIL_TAKEN"));
            case EmailChangeService.EmailConfirmation.InvalidToken invalid ->
                    HttpResponse.badRequest().body(Map.of("status", "INVALID_TOKEN"));
        };
    }
}

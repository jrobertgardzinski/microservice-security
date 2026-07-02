package com.jrobertgardzinski;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.vo.PurgeChoices;
import com.jrobertgardzinski.security.system.account.StartAccountDeletion;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Post;
import io.micronaut.core.annotation.Nullable;

import java.util.Optional;

import java.util.Map;

/**
 * HTTP entry point for closing an account. Protected: {@link AuthorizationFilter} has authorized the
 * access token and published the caller's email. Closing is a saga: {@link StartAccountDeletion}
 * locks the account at once (sessions revoked, sign-in refused) and asks microservice-memes to
 * purge the user's content; the confirmation — not this request — deletes the user for good.
 */
// controllers do blocking work (JDBC, the mail service's HTTP client) — keep it off the event loop
@ExecuteOn(TaskExecutors.BLOCKING)
@Controller("/account/delete")
final class DeleteAccountController {

    private final StartAccountDeletion startAccountDeletion;
    private final TransactionBoundary transactionBoundary;

    DeleteAccountController(StartAccountDeletion startAccountDeletion, TransactionBoundary transactionBoundary) {
        this.startAccountDeletion = startAccountDeletion;
        this.transactionBoundary = transactionBoundary;
    }

    @Post(consumes = MediaType.ALL, produces = MediaType.APPLICATION_JSON)
    HttpResponse<Map<String, Object>> delete(HttpRequest<?> request, @Body @Nullable Map<String, Map<String, String>> body) {
        Email email = Email.of(request.getAttribute(AuthorizationFilter.AUTHENTICATED_EMAIL, String.class).orElseThrow());
        PurgeChoices choices = purgeChoices(body);
        transactionBoundary.execute(() -> {
            startAccountDeletion.execute(email, choices);
            return null;
        });
        return HttpResponse.accepted().body(Map.of("status", "ACCOUNT_DELETION_STARTED"));
    }

    /**
     * The wizard's choice: {@code {"purge": {"memes": "...", "comments": "..."}}}, both axes or
     * none. The rule strings stay opaque here — their vocabulary belongs to the meme service.
     */
    private static PurgeChoices purgeChoices(Map<String, Map<String, String>> body) {
        Map<String, String> purge = body == null ? null : body.get("purge");
        if (purge == null || purge.get("memes") == null || purge.get("comments") == null) {
            return PurgeChoices.serviceDefaults();
        }
        return new PurgeChoices(Optional.of(purge.get("memes")), Optional.of(purge.get("comments")));
    }
}

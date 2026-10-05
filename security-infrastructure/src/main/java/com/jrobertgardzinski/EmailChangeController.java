package com.jrobertgardzinski;

import com.jrobertgardzinski.security.application.account.AccountService;
import com.jrobertgardzinski.security.domain.mfa.StepUpAction;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;

import java.util.Map;

/**
 * HTTP entry point to start an email change. Protected: {@link AuthorizationFilter} has authorized
 * the access token and published the caller's current email; here we request a change to the new
 * address, which e-mails a verification link there (confirmed separately, pre-login).
 *
 * <p>Same anti-enumeration stance as {@code /register}: a taken address answers exactly like a
 * fresh request, and the OWNER of that address learns by mail that someone tried to use it. A
 * signed-in caller probing addresses gets nothing the anonymous one would not.
 */
// controllers do blocking work (JDBC, the mail service's HTTP client) — keep it off the event loop
@ExecuteOn(TaskExecutors.BLOCKING)
@Controller("/account/email")
final class EmailChangeController {

    private final AccountService account;
    private final StepUpGuard stepUpGuard;

    EmailChangeController(AccountService account, StepUpGuard stepUpGuard) {
        this.account = account;
        this.stepUpGuard = stepUpGuard;
    }

    @Post(value = "/request", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
    HttpResponse<?> request(HttpRequest<?> request, @Body Map<String, Object> body) {
        // moving the address moves the account, and the confirmation lands in the NEW mailbox — so
        // a thief with a live session could walk off with the whole account and the owner would
        // learn about it from a notice. Guarded where the change STARTS; the confirmation itself
        // still needs the token mailed to that new address.
        AccountService.EmailChange outcome = account.requestEmailChange(Caller.of(request),
                JsonBody.text(body, "newEmail"),
                () -> stepUpGuard.requireElevation(request, StepUpAction.CHANGE_EMAIL).isEmpty());
        return switch (outcome) {
            case AccountService.EmailChange.LinkSent sent ->
                    HttpResponse.accepted().body(Map.of("status", "EMAIL_CHANGE_LINK_SENT"));
            case AccountService.EmailChange.Rejected rejected ->
                    HttpResponse.unprocessableEntity().body(Map.of(
                            "emailErrors",
                            SecurityController.emailErrors(rejected.emailErrors(), rejected.emailPolicy())));
            case AccountService.EmailChange.InvalidEmail invalid ->
                    HttpResponse.badRequest().body(Map.of("status", "INVALID_EMAIL"));
            case AccountService.EmailChange.StepUpRequired stepUp -> StepUpGuard.refusal(StepUpAction.CHANGE_EMAIL);
        };
    }
}

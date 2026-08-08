package com.jrobertgardzinski;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.system.account.RequestEmailChange;
import com.jrobertgardzinski.security.system.account.RequestEmailChangeResult;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.http.annotation.Post;

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

    private final RequestEmailChange requestEmailChange;
    private final TransactionBoundary transactionBoundary;
    private final com.jrobertgardzinski.security.domain.port.RegistrationNoticeNotifier noticeNotifier;

    EmailChangeController(RequestEmailChange requestEmailChange, TransactionBoundary transactionBoundary,
                          com.jrobertgardzinski.security.domain.port.RegistrationNoticeNotifier noticeNotifier) {
        this.requestEmailChange = requestEmailChange;
        this.transactionBoundary = transactionBoundary;
        this.noticeNotifier = noticeNotifier;
    }

    @Post(value = "/request", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
    HttpResponse<?> request(HttpRequest<?> request, @Body Map<String, Object> body) {
        Email currentEmail = Email.of(
                request.getAttribute(AuthorizationFilter.AUTHENTICATED_EMAIL, String.class).orElseThrow());
        Email newEmail;
        try {
            newEmail = Email.of((String) body.get("newEmail"));
        } catch (IllegalArgumentException invalid) {
            return HttpResponse.badRequest().body(Map.of("status", "INVALID_EMAIL"));
        }
        RequestEmailChangeResult result = transactionBoundary.execute(
                () -> requestEmailChange.execute(currentEmail, newEmail));
        return switch (result) {
            case RequestEmailChangeResult.Requested ignored ->
                    HttpResponse.accepted().body(Map.of("status", "EMAIL_CHANGE_LINK_SENT"));
            case RequestEmailChangeResult.EmailTaken ignored -> {
                // quiet refusal: the wire looks like a fresh request; the address owner is told by mail
                transactionBoundary.execute(() -> {
                    noticeNotifier.sendAlreadyRegistered(newEmail);
                    return null;
                });
                yield HttpResponse.accepted().body(Map.of("status", "EMAIL_CHANGE_LINK_SENT"));
            }
        };
    }
}

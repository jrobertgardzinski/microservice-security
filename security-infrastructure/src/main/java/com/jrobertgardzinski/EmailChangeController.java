package com.jrobertgardzinski;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.system.account.RequestEmailChange;
import com.jrobertgardzinski.security.system.account.RequestEmailChangeResult;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;

import java.util.Map;

/**
 * HTTP entry point to start an email change. Protected: {@link AuthorizationFilter} has authorized
 * the access token and published the caller's current email; here we request a change to the new
 * address, which e-mails a verification link there (confirmed separately, pre-login).
 */
@Controller("/account/email")
final class EmailChangeController {

    private final RequestEmailChange requestEmailChange;
    private final TransactionBoundary transactionBoundary;

    EmailChangeController(RequestEmailChange requestEmailChange, TransactionBoundary transactionBoundary) {
        this.requestEmailChange = requestEmailChange;
        this.transactionBoundary = transactionBoundary;
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
            case RequestEmailChangeResult.EmailTaken ignored ->
                    HttpResponse.status(io.micronaut.http.HttpStatus.CONFLICT).body(Map.of("status", "EMAIL_TAKEN"));
        };
    }
}

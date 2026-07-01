package com.jrobertgardzinski;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.system.account.DeleteAccount;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;

import java.util.Map;

/**
 * HTTP entry point for closing an account. Protected: {@link AuthorizationFilter} has authorized the
 * access token and published the caller's email; here we delete that account and revoke its
 * sessions via the {@link DeleteAccount} use case, so the presented token stops working right after.
 */
@Controller("/account/delete")
final class DeleteAccountController {

    private final DeleteAccount deleteAccount;
    private final TransactionBoundary transactionBoundary;

    DeleteAccountController(DeleteAccount deleteAccount, TransactionBoundary transactionBoundary) {
        this.deleteAccount = deleteAccount;
        this.transactionBoundary = transactionBoundary;
    }

    @Post(consumes = MediaType.ALL, produces = MediaType.APPLICATION_JSON)
    HttpResponse<Map<String, Object>> delete(HttpRequest<?> request) {
        Email email = Email.of(request.getAttribute(AuthorizationFilter.AUTHENTICATED_EMAIL, String.class).orElseThrow());
        transactionBoundary.execute(() -> {
            deleteAccount.execute(email);
            return null;
        });
        return HttpResponse.ok(Map.of("status", "ACCOUNT_CLOSED"));
    }
}

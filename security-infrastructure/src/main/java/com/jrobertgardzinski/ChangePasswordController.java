package com.jrobertgardzinski;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.password.domain.PlaintextPassword;
import com.jrobertgardzinski.security.system.account.ChangePassword;
import com.jrobertgardzinski.security.system.account.ChangePasswordResult;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;

import java.util.Map;

/**
 * HTTP entry point for changing a password. A protected endpoint: {@link AuthorizationFilter} has
 * already authorized the access token and published the caller's email, so we change that user's
 * password once their current password checks out, driving the {@link ChangePassword} use case.
 */
@Controller("/account/password")
final class ChangePasswordController {

    private final ChangePassword changePassword;
    private final TransactionBoundary transactionBoundary;

    ChangePasswordController(ChangePassword changePassword, TransactionBoundary transactionBoundary) {
        this.changePassword = changePassword;
        this.transactionBoundary = transactionBoundary;
    }

    @Post(consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
    HttpResponse<?> change(HttpRequest<?> request, @Body Map<String, Object> body) {
        Email email = Email.of(request.getAttribute(AuthorizationFilter.AUTHENTICATED_EMAIL, String.class).orElseThrow());
        String current = (String) body.get("currentPassword");
        String next = (String) body.get("newPassword");
        ChangePasswordResult result = transactionBoundary.execute(() -> changePassword.execute(
                email, () -> PlaintextPassword.of(current), () -> PlaintextPassword.of(next)));
        return switch (result) {
            case ChangePasswordResult.Changed ignored ->
                    HttpResponse.ok(Map.of("status", "PASSWORD_CHANGED"));
            case ChangePasswordResult.WrongCurrentPassword ignored ->
                    HttpResponse.badRequest().body(Map.of("status", "WRONG_CURRENT_PASSWORD"));
            case ChangePasswordResult.WeakPassword ignored ->
                    HttpResponse.badRequest().body(Map.of("status", "WEAK_PASSWORD"));
        };
    }
}

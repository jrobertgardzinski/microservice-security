package com.jrobertgardzinski;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.password.domain.PlaintextPassword;
import com.jrobertgardzinski.security.domain.vo.token.PasswordResetToken;
import com.jrobertgardzinski.security.system.passwordreset.RequestPasswordReset;
import com.jrobertgardzinski.security.system.passwordreset.ResetPassword;
import com.jrobertgardzinski.security.system.passwordreset.ResetPasswordResult;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.http.annotation.Post;

import java.util.Map;

/**
 * HTTP entry points for the forgotten-password flow, driving the {@link RequestPasswordReset} and
 * {@link ResetPassword} use cases. Public (pre-login): {@code POST /reset-password/request} mails a
 * link, {@code POST /reset-password} sets a new password with the token from that link.
 */
// controllers do blocking work (JDBC, the mail service's HTTP client) — keep it off the event loop
@ExecuteOn(TaskExecutors.BLOCKING)
@Controller("/reset-password")
final class PasswordResetController {

    private final RequestPasswordReset requestPasswordReset;
    private final ResetPassword resetPassword;
    private final TransactionBoundary transactionBoundary;

    PasswordResetController(RequestPasswordReset requestPasswordReset, ResetPassword resetPassword,
                            TransactionBoundary transactionBoundary) {
        this.requestPasswordReset = requestPasswordReset;
        this.resetPassword = resetPassword;
        this.transactionBoundary = transactionBoundary;
    }

    @Post(value = "/request", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
    HttpResponse<?> request(@Body Map<String, Object> body) {
        Email email = Email.of((String) body.get("email"));
        transactionBoundary.execute(() -> {
            requestPasswordReset.execute(email);
            return null;
        });
        return HttpResponse.accepted().body(Map.of("status", "RESET_LINK_SENT"));
    }

    @Post(consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
    HttpResponse<?> reset(@Body Map<String, Object> body) {
        PasswordResetToken token = new PasswordResetToken((String) body.get("token"));
        String password = (String) body.get("password");
        ResetPasswordResult result = transactionBoundary.execute(
                () -> resetPassword.execute(token, () -> PlaintextPassword.of(password)));
        return switch (result) {
            case ResetPasswordResult.PasswordReset reset ->
                    HttpResponse.ok(Map.of("status", "PASSWORD_RESET", "email", reset.email().value()));
            case ResetPasswordResult.WeakPassword ignored ->
                    HttpResponse.badRequest().body(Map.of("status", "WEAK_PASSWORD"));
            case ResetPasswordResult.InvalidToken ignored ->
                    HttpResponse.badRequest().body(Map.of("status", "INVALID_TOKEN"));
        };
    }
}

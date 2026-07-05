package com.jrobertgardzinski;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.password.domain.PlaintextPassword;
import com.jrobertgardzinski.security.domain.vo.token.PasswordResetToken;
import com.jrobertgardzinski.security.domain.vo.IpAddress;
import com.jrobertgardzinski.security.system.passwordreset.RequestPasswordReset;
import com.jrobertgardzinski.security.system.passwordreset.ResetPassword;
import com.jrobertgardzinski.security.system.passwordreset.ResetPasswordResult;
import com.jrobertgardzinski.security.system.throttle.SourceThrottle;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.http.annotation.Post;
import jakarta.inject.Named;

import java.util.Map;

/**
 * HTTP entry points for the forgotten-password flow, driving the {@link RequestPasswordReset} and
 * {@link ResetPassword} use cases. Public (pre-login): {@code POST /reset-password/request} mails a
 * link, {@code POST /reset-password} sets a new password with the token from that link. The
 * request side is throttled per source (429 + Retry-After) — it mints tokens and sends mails, so
 * unthrottled it is a mail-bomb aimed at any address the caller types in.
 */
// controllers do blocking work (JDBC, the mail service's HTTP client) — keep it off the event loop
@ExecuteOn(TaskExecutors.BLOCKING)
@Controller("/reset-password")
final class PasswordResetController {

    private final RequestPasswordReset requestPasswordReset;
    private final ResetPassword resetPassword;
    private final TransactionBoundary transactionBoundary;
    private final SourceThrottle throttle;
    private final ClientIpResolver clientIpResolver;

    PasswordResetController(RequestPasswordReset requestPasswordReset, ResetPassword resetPassword,
                            TransactionBoundary transactionBoundary,
                            @Named("password-reset") SourceThrottle throttle,
                            ClientIpResolver clientIpResolver) {
        this.requestPasswordReset = requestPasswordReset;
        this.resetPassword = resetPassword;
        this.transactionBoundary = transactionBoundary;
        this.throttle = throttle;
        this.clientIpResolver = clientIpResolver;
    }

    @Post(value = "/request", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
    HttpResponse<?> request(HttpRequest<?> httpRequest, @Body Map<String, Object> body) {
        IpAddress source = clientIpResolver.resolve(httpRequest);
        SourceThrottle.Decision decision = throttle.check(source);
        if (!decision.allowed()) {
            return HttpResponse.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header("Retry-After", String.valueOf(decision.retryAfterSeconds()))
                    .body(Map.of("error", "TOO_MANY_RESET_REQUESTS"));
        }
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

package com.jrobertgardzinski;

import com.jrobertgardzinski.security.application.mailbox.PasswordResetService;
import io.micronaut.http.HttpRequest;
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
 * HTTP entry points for the forgotten-password flow, through
 * {@link com.jrobertgardzinski.security.application.mailbox.PasswordResetService}. Public (pre-login): {@code POST /reset-password/request} mails a
 * link, {@code POST /reset-password} sets a new password with the token from that link. The
 * request side is throttled per source (429 + Retry-After) — it mints tokens and sends mails, so
 * unthrottled it is a mail-bomb aimed at any address the caller types in.
 */
// controllers do blocking work (JDBC, the mail service's HTTP client) — keep it off the event loop
@ExecuteOn(TaskExecutors.BLOCKING)
@Controller("/reset-password")
final class PasswordResetController {

    private final PasswordResetService passwordReset;
    private final ClientIpResolver clientIpResolver;

    PasswordResetController(PasswordResetService passwordReset, ClientIpResolver clientIpResolver) {
        this.passwordReset = passwordReset;
        this.clientIpResolver = clientIpResolver;
    }

    @Post(value = "/request", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
    HttpResponse<?> request(HttpRequest<?> httpRequest, @Body Map<String, Object> body) {
        return switch (passwordReset.requestLink(JsonBody.text(body, "email"), clientIpResolver.resolve(httpRequest))) {
            case PasswordResetService.Request.LinkSent sent ->
                    HttpResponse.accepted().body(Map.of("status", "RESET_LINK_SENT"));
            case PasswordResetService.Request.Throttled throttled ->
                    HttpResponse.status(HttpStatus.TOO_MANY_REQUESTS)
                            .header("Retry-After", String.valueOf(throttled.retryAfterSeconds()))
                            .body(Map.of("error", "TOO_MANY_RESET_REQUESTS"));
        };
    }

    @Post(consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
    HttpResponse<?> reset(@Body Map<String, Object> body) {
        return switch (passwordReset.reset(JsonBody.text(body, "token"), JsonBody.text(body, "password"))) {
            case PasswordResetService.Reset.PasswordReset reset ->
                    HttpResponse.ok(Map.of("status", "PASSWORD_RESET", "email", reset.email().value()));
            case PasswordResetService.Reset.WeakPassword weak ->
                    HttpResponse.badRequest().body(Map.of("status", "WEAK_PASSWORD"));
            case PasswordResetService.Reset.InvalidToken invalid ->
                    HttpResponse.badRequest().body(Map.of("status", "INVALID_TOKEN"));
        };
    }
}

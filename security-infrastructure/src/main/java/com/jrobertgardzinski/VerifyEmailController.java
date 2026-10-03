package com.jrobertgardzinski;

import com.jrobertgardzinski.security.application.verification.VerificationService;
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
 * HTTP entry points for e-mail verification, through
 * {@link com.jrobertgardzinski.security.application.verification.VerificationService}. Public (pre-login): {@code POST /verify-email/request} mails a
 * link, {@code POST /verify-email} confirms the token from that link. The request side is
 * throttled per source (429 + Retry-After) — it mints tokens and sends mails, so unthrottled it
 * is a mail-bomb aimed at any address the caller types in.
 */
// controllers do blocking work (JDBC, the mail service's HTTP client) — keep it off the event loop
@ExecuteOn(TaskExecutors.BLOCKING)
@Controller("/verify-email")
final class VerifyEmailController {

    private final VerificationService verification;
    private final ClientIpResolver clientIpResolver;

    VerifyEmailController(VerificationService verification, ClientIpResolver clientIpResolver) {
        this.verification = verification;
        this.clientIpResolver = clientIpResolver;
    }

    @Post(value = "/request", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
    HttpResponse<?> request(HttpRequest<?> httpRequest, @Body Map<String, Object> body) {
        return switch (verification.requestLink(JsonBody.text(body, "email"), clientIpResolver.resolve(httpRequest))) {
            case VerificationService.Request.LinkSent sent ->
                    HttpResponse.accepted().body(Map.of("status", "VERIFICATION_LINK_SENT"));
            case VerificationService.Request.Throttled throttled ->
                    HttpResponse.status(HttpStatus.TOO_MANY_REQUESTS)
                            .header("Retry-After", String.valueOf(throttled.retryAfterSeconds()))
                            .body(Refusal.alsoAsError("TOO_MANY_VERIFICATION_REQUESTS"));
        };
    }

    @Post(consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
    HttpResponse<?> verify(@Body Map<String, Object> body) {
        return switch (verification.verify(JsonBody.text(body, "token"))) {
            case VerificationService.Verify.Verified verified ->
                    HttpResponse.ok(Map.of("status", "EMAIL_VERIFIED", "email", verified.email().value()));
            case VerificationService.Verify.InvalidToken invalid ->
                    HttpResponse.badRequest().body(Map.of("status", "INVALID_TOKEN"));
        };
    }
}

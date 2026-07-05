package com.jrobertgardzinski;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.vo.IpAddress;
import com.jrobertgardzinski.security.domain.vo.token.VerificationToken;
import com.jrobertgardzinski.security.system.throttle.SourceThrottle;
import com.jrobertgardzinski.security.system.verification.RequestEmailVerification;
import com.jrobertgardzinski.security.system.verification.VerifyEmail;
import com.jrobertgardzinski.security.system.verification.VerifyEmailResult;
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
 * HTTP entry points for e-mail verification, driving the {@link RequestEmailVerification} and
 * {@link VerifyEmail} use cases. Public (pre-login): {@code POST /verify-email/request} mails a
 * link, {@code POST /verify-email} confirms the token from that link. The request side is
 * throttled per source (429 + Retry-After) — it mints tokens and sends mails, so unthrottled it
 * is a mail-bomb aimed at any address the caller types in.
 */
// controllers do blocking work (JDBC, the mail service's HTTP client) — keep it off the event loop
@ExecuteOn(TaskExecutors.BLOCKING)
@Controller("/verify-email")
final class VerifyEmailController {

    private final RequestEmailVerification requestEmailVerification;
    private final VerifyEmail verifyEmail;
    private final TransactionBoundary transactionBoundary;
    private final SourceThrottle throttle;
    private final ClientIpResolver clientIpResolver;

    VerifyEmailController(RequestEmailVerification requestEmailVerification, VerifyEmail verifyEmail,
                          TransactionBoundary transactionBoundary,
                          @Named("verification") SourceThrottle throttle,
                          ClientIpResolver clientIpResolver) {
        this.requestEmailVerification = requestEmailVerification;
        this.verifyEmail = verifyEmail;
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
                    .body(Map.of("error", "TOO_MANY_VERIFICATION_REQUESTS"));
        }
        Email email = Email.of((String) body.get("email"));
        transactionBoundary.execute(() -> {
            requestEmailVerification.execute(email);
            return null;
        });
        return HttpResponse.accepted().body(Map.of("status", "VERIFICATION_LINK_SENT"));
    }

    @Post(consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
    HttpResponse<?> verify(@Body Map<String, Object> body) {
        VerificationToken token = new VerificationToken((String) body.get("token"));
        VerifyEmailResult result = transactionBoundary.execute(() -> verifyEmail.execute(token));
        if (result instanceof VerifyEmailResult.Verified verified) {
            return HttpResponse.ok(Map.of("status", "EMAIL_VERIFIED", "email", verified.email().value()));
        }
        return HttpResponse.badRequest().body(Map.of("status", "INVALID_TOKEN"));
    }
}

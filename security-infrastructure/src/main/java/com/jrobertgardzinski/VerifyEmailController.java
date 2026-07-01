package com.jrobertgardzinski;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.vo.token.VerificationToken;
import com.jrobertgardzinski.security.system.verification.RequestEmailVerification;
import com.jrobertgardzinski.security.system.verification.VerifyEmail;
import com.jrobertgardzinski.security.system.verification.VerifyEmailResult;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;

import java.util.Map;

/**
 * HTTP entry points for e-mail verification, driving the {@link RequestEmailVerification} and
 * {@link VerifyEmail} use cases. Public (pre-login): {@code POST /verify-email/request} mails a
 * link, {@code POST /verify-email} confirms the token from that link.
 */
@Controller("/verify-email")
final class VerifyEmailController {

    private final RequestEmailVerification requestEmailVerification;
    private final VerifyEmail verifyEmail;
    private final TransactionBoundary transactionBoundary;

    VerifyEmailController(RequestEmailVerification requestEmailVerification, VerifyEmail verifyEmail,
                          TransactionBoundary transactionBoundary) {
        this.requestEmailVerification = requestEmailVerification;
        this.verifyEmail = verifyEmail;
        this.transactionBoundary = transactionBoundary;
    }

    @Post(value = "/request", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
    HttpResponse<?> request(@Body Map<String, Object> body) {
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

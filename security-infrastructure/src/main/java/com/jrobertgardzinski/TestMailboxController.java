package com.jrobertgardzinski;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;

import java.util.Map;

/**
 * Test-only window into the captured "mailbox". Present only under the {@code test} environment —
 * the same rule as the steerable clock's {@code /test/clock} — so an out-of-process test harness
 * (the Angular UI's cucumber-js/Playwright runner) can read the verification token a scenario's
 * registration "mailed", exactly like the in-process glue reads the capturing notifier bean.
 * A production build never has the capturing notifier, let alone this controller.
 */
@Controller("/test/mailbox")
@Requires(env = "test")
final class TestMailboxController {

    private final CapturingEmailVerificationNotifier verificationMails;
    private final CapturingEmailCodeChannel signInCodes;
    private final CapturingPasswordResetNotifier resetMails;
    private final CapturingRegistrationNoticeNotifier notices;

    TestMailboxController(CapturingEmailVerificationNotifier verificationMails,
                          CapturingEmailCodeChannel signInCodes,
                          CapturingPasswordResetNotifier resetMails,
                          CapturingRegistrationNoticeNotifier notices) {
        this.verificationMails = verificationMails;
        this.signInCodes = signInCodes;
        this.resetMails = resetMails;
        this.notices = notices;
    }

    @Get(value = "/verification-token", produces = MediaType.APPLICATION_JSON)
    HttpResponse<Map<String, String>> verificationToken(@QueryValue String email) {
        String token = verificationMails.lastTokenFor(email);
        return token == null
                ? HttpResponse.notFound(Map.of("error", "NO_MAIL_FOR_" + email))
                : HttpResponse.ok(Map.of("token", token));
    }

    /** The last password-reset token "mailed" to an address. */
    @Get(value = "/reset-token", produces = MediaType.APPLICATION_JSON)
    HttpResponse<Map<String, String>> resetToken(@QueryValue String email) {
        String token = resetMails.lastTokenFor(email);
        return token == null
                ? HttpResponse.notFound(Map.of("error", "NO_MAIL_FOR_" + email))
                : HttpResponse.ok(Map.of("token", token));
    }

    /** Whether an "already registered / someone used your address" notice was mailed there. */
    @Get(value = "/notice", produces = MediaType.APPLICATION_JSON)
    HttpResponse<Map<String, String>> notice(@QueryValue String email) {
        return notices.noticedEmails().contains(email)
                ? HttpResponse.ok(Map.of("noticed", email))
                : HttpResponse.notFound(Map.of("error", "NO_NOTICE_FOR_" + email));
    }

    /** The last one-time sign-in / enrolment code "mailed" to an address (the AUTH_CODE mail). */
    @Get(value = "/signin-code", produces = MediaType.APPLICATION_JSON)
    HttpResponse<Map<String, String>> signinCode(@QueryValue String email) {
        String code = signInCodes.lastCodeFor(email);
        return code == null
                ? HttpResponse.notFound(Map.of("error", "NO_CODE_FOR_" + email))
                : HttpResponse.ok(Map.of("code", code));
    }
}

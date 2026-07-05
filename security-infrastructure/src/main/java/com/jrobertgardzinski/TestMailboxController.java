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

    TestMailboxController(CapturingEmailVerificationNotifier verificationMails) {
        this.verificationMails = verificationMails;
    }

    @Get(value = "/verification-token", produces = MediaType.APPLICATION_JSON)
    HttpResponse<Map<String, String>> verificationToken(@QueryValue String email) {
        String token = verificationMails.lastTokenFor(email);
        return token == null
                ? HttpResponse.notFound(Map.of("error", "NO_MAIL_FOR_" + email))
                : HttpResponse.ok(Map.of("token", token));
    }
}

package com.jrobertgardzinski;

import com.jrobertgardzinski.security.application.account.AccountDeletionService;
import com.jrobertgardzinski.security.domain.vo.StepUpAction;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static com.jrobertgardzinski.MaskedEmail.masked;

/**
 * The ONE door out of an account, for the person whose account it is and for an administrator
 * closing somebody else's. Which of the two it is comes from the request itself — the address in
 * the path against the address in the token — and everything else follows from that comparison:
 * what must be proven, and whether the caller may say what happens to the content.
 *
 * <ul>
 *   <li><strong>Your own account.</strong> A fresh step-up ({@link StepUpAction#DELETE_ACCOUNT}),
 *       and then everything you ever posted goes. This request IS the right to erasure, and the
 *       grounds on which content may survive such a request are enumerated by law — "the community
 *       up-voted it" is not one of them — so any conditions in the body are dropped, by
 *       {@code AccountClosure} itself rather than by this controller remembering to.</li>
 *   <li><strong>Somebody else's.</strong> The ADMIN role on top of the step-up
 *       ({@link StepUpAction#ADMIN_DELETE_ACCOUNT}), because this destroys another person's
 *       account and their content on one press. Nobody is exercising a right here — it is a ban,
 *       or house rules — so the caller MAY state a rule per content axis, and it rides the saga.</li>
 * </ul>
 *
 * <p>The two used to be two routes. One is closer to the truth: "am I closing my own account or
 * someone else's" is a property of the request, not of the URL, and the authorisation is what
 * follows from it rather than what picks it.
 *
 * <p>Protected: {@link AuthorizationFilter} has authorized the access token and published the
 * caller's email. Closing is a saga, started by
 * {@link com.jrobertgardzinski.security.application.account.AccountDeletionService}: the account
 * is locked at once and the content services are asked to purge; their confirmation — not this
 * request — deletes the account for good.
 */
// controllers do blocking work (JDBC, the mail service's HTTP client) — keep it off the event loop
@ExecuteOn(TaskExecutors.BLOCKING)
@Controller("/account")
final class DeleteAccountController {

    private static final Logger LOG = LoggerFactory.getLogger(DeleteAccountController.class);

    private final AccountDeletionService accountDeletion;
    private final StepUpGuard stepUpGuard;

    DeleteAccountController(AccountDeletionService accountDeletion, StepUpGuard stepUpGuard) {
        this.accountDeletion = accountDeletion;
        this.stepUpGuard = stepUpGuard;
    }

    @Delete(value = "/{email}", consumes = MediaType.ALL, produces = MediaType.APPLICATION_JSON)
    HttpResponse<Map<String, Object>> delete(HttpRequest<?> request, @PathVariable String email,
                                             @Body @Nullable Map<String, Map<String, String>> body) {
        AccountDeletionService.Closure closure = accountDeletion.start(Caller.of(request), email,
                body == null ? null : body.get("purge"),
                () -> stepUpGuard.requireElevation(request, StepUpAction.DELETE_ACCOUNT).isEmpty(),
                () -> stepUpGuard.requireElevation(request, StepUpAction.ADMIN_DELETE_ACCOUNT).isEmpty());
        return switch (closure) {
            case AccountDeletionService.Closure.StartedByOwner started -> accepted();
            case AccountDeletionService.Closure.StartedByAdministrator started -> {
                // the audit line, and the only place the two addresses ever meet: the fact on the
                // wire says ADMIN and not WHICH admin, because three content services would then
                // hold an extra person's address for no use of their own
                LOG.warn("account deletion started for {} by administrator {}",
                        masked(started.account().value()), masked(started.administrator().value()));
                yield accepted();
            }
            case AccountDeletionService.Closure.InvalidEmail invalid ->
                    HttpResponse.<Map<String, Object>>status(HttpStatus.BAD_REQUEST)
                            .body(Map.of("status", "INVALID_EMAIL"));
            case AccountDeletionService.Closure.NotPermitted notPermitted ->
                    HttpResponse.<Map<String, Object>>status(HttpStatus.FORBIDDEN)
                            .body(Map.of("status", notPermitted.code()));
            case AccountDeletionService.Closure.StepUpRequired stepUp -> StepUpGuard.refusal(stepUp.action());
            // an over-large or over-wide purge map is a bad request, not a server fault
            case AccountDeletionService.Closure.InvalidPurgeChoices invalid ->
                    HttpResponse.<Map<String, Object>>status(HttpStatus.UNPROCESSABLE_ENTITY)
                            .body(Map.of("status", "INVALID_PURGE_CHOICES"));
            case AccountDeletionService.Closure.NoSuchUser none ->
                    HttpResponse.<Map<String, Object>>status(HttpStatus.NOT_FOUND)
                            .body(Map.of("status", "NO_SUCH_USER"));
        };
    }

    private static HttpResponse<Map<String, Object>> accepted() {
        return HttpResponse.accepted().body(Map.of("status", "ACCOUNT_DELETION_STARTED"));
    }
}

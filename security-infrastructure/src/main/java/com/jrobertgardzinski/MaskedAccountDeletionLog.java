package com.jrobertgardzinski;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.account.AccountDeletionLog;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;

import static com.jrobertgardzinski.MaskedEmail.masked;

/** The account-deletion saga's log lines, every address in them masked. */
@Singleton
final class MaskedAccountDeletionLog implements AccountDeletionLog {

    private static final Logger LOG = LoggerFactory.getLogger(MaskedAccountDeletionLog.class);

    @Override
    public void deletedImmediately(Email leaver) {
        LOG.info("account deleted immediately (no portal configured) for {}", masked(leaver.value()));
    }

    @Override
    public void alreadyUnderWay(Email leaver) {
        LOG.info("account deletion already under way for {}; joining it instead of announcing"
                + " a second one", masked(leaver.value()));
    }

    @Override
    public void completed(Email leaver) {
        LOG.info("account deletion completed for {}", masked(leaver.value()));
    }

    /**
     * An ERROR, and it used to share one INFO line with a harmless duplicate — so the whole event
     * was a line nobody greps for.
     */
    @Override
    public void erasedAfterCompensation(Email leaver) {
        LOG.error("CONTENT ERASED AFTER COMPENSATION for {}: the portal confirmed the purge"
                        + " after this service had already given up, unlocked the account and"
                        + " apologised. The account exists; its content does not. This needs"
                        + " a human — the deletion timeout here and the portal's retry budget"
                        + " are independent dials in separate repositories.",
                masked(leaver.value()));
    }

    @Override
    public void strayConfirmation(Email leaver) {
        LOG.info("portal-purged outcome for {} matched no running deletion; ignoring", masked(leaver.value()));
    }

    @Override
    public void strayFailure(Email leaver) {
        LOG.info("purge-failed outcome for {} matched no running deletion; ignoring", masked(leaver.value()));
    }

    @Override
    public void compensated(Email leaver, List<String> reserved) {
        if (reserved.isEmpty()) {
            LOG.warn("account deletion compensated (portal reported a failed purge) for {}",
                    masked(leaver.value()));
            return;
        }
        // The list is who CONFIRMED, and since ADR 0007 that means "reserved", not "destroyed":
        // a portal participant marks the leaver's content, hides it from every read and keeps
        // it, and the same capitulation that produced this outcome commands the restore. So
        // this line does not cry about content that will not come back — it says who had it
        // hidden, which is still worth an operator's eye if the restore itself goes missing.
        LOG.warn("account deletion compensated for {}; the portal had already RESERVED the"
                        + " content at {} and is restoring it — if it does not come back,"
                        + " that is a portal-side problem, not a lost purge",
                masked(leaver.value()), reserved);
    }

    @Override
    public void compensatedOverdue(Email leaver, Duration timeout) {
        LOG.warn("account deletion compensated (no portal outcome in {}) for {}", timeout, masked(leaver.value()));
    }
}

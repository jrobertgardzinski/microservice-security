package com.jrobertgardzinski;

import com.jrobertgardzinski.security.application.TransactionBoundary;
import com.jrobertgardzinski.security.application.account.AccountDeletionService;
import io.micronaut.context.annotation.Requires;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;

/**
 * Clock-driven edge of the saga: periodically rolls back deletions whose purge confirmation never
 * arrived. Off in the test environment — the Gherkin steps drive the saga directly, with
 * the steerable clock deciding what "overdue" means.
 */
@Singleton
@Requires(notEnv = "test")
class AccountDeletionTimeouts {

    private static final org.slf4j.Logger LOG =
            org.slf4j.LoggerFactory.getLogger(AccountDeletionTimeouts.class);

    private final AccountDeletionService accountDeletion;
    private final TransactionBoundary transactionBoundary;

    AccountDeletionTimeouts(AccountDeletionService accountDeletion, TransactionBoundary transactionBoundary) {
        this.accountDeletion = accountDeletion;
        this.transactionBoundary = transactionBoundary;
    }

    @Scheduled(fixedDelay = "30s", initialDelay = "30s")
    void tick() {
        try {
            transactionBoundary.execute(() -> {
                accountDeletion.compensateOverdue();
                return null;
            });
        } catch (RuntimeException sweepFailed) {
            // the same rule the retention reapers follow: a tick that cannot run is not a request
            // that fails, and it must not be the last one. This is the ONLY thing that ever unlocks
            // an account whose portal outcome never arrived, so a scheduler that stops here leaves
            // people locked out of their own accounts with nothing else coming to free them.
            LOG.warn("the deletion timeout sweep could not run: {}", sweepFailed.toString());
        }
    }
}

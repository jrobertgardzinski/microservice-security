package com.jrobertgardzinski;

import io.micronaut.context.annotation.Requires;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;

/**
 * Clock-driven edge of the saga: periodically rolls back deletions whose purge confirmation never
 * arrived. Off in the test environment — the Gherkin steps drive the orchestrator directly, with
 * the steerable clock deciding what "overdue" means.
 */
@Singleton
@Requires(notEnv = "test")
class AccountDeletionTimeouts {

    private final AccountDeletionOrchestrator orchestrator;
    private final TransactionBoundary transactionBoundary;

    AccountDeletionTimeouts(AccountDeletionOrchestrator orchestrator, TransactionBoundary transactionBoundary) {
        this.orchestrator = orchestrator;
        this.transactionBoundary = transactionBoundary;
    }

    @Scheduled(fixedDelay = "30s", initialDelay = "30s")
    void tick() {
        transactionBoundary.execute(() -> {
            orchestrator.compensateOverdue();
            return null;
        });
    }
}

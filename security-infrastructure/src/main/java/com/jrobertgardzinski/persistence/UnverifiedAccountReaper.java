package com.jrobertgardzinski.persistence;

import com.jrobertgardzinski.TransactionBoundary;
import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.config.retention.vo.UnverifiedAccountDays;
import com.jrobertgardzinski.security.system.account.DeleteAccount;
import io.micronaut.context.annotation.Requires;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Deletes accounts whose address was never verified.
 *
 * <p>The other half of the same story as {@code AbandonedVerificationReaper}: that one sweeps the
 * pending token, this one sweeps the account it was for. Between them, a month after somebody typed
 * an address and never confirmed it, nothing of the attempt remains — which is what makes the
 * period one number rather than two rules.
 *
 * <p>It goes through {@link DeleteAccount}, not a DELETE: an account is not one row. Sessions,
 * factors, recovery codes, federated links and every address-keyed pending token belong to it, and
 * the use case is the one place that knows the whole list. There is nothing to purge in the portal
 * — an unverified account cannot sign in, so it has never posted anything — which is why this does
 * not open a deletion saga.
 *
 * <p>How long is a DEPLOYMENT's choice ({@link UnverifiedAccountDays}), because the law states a
 * principle and not a number. The value here is whatever the ladder resolved at startup: the code
 * default, or the deployment's property over it.
 */
@Singleton
@Requires(beans = DataSource.class)
class UnverifiedAccountReaper {

    private static final Logger LOG = LoggerFactory.getLogger(UnverifiedAccountReaper.class);

    private final UserJdbcRepository users;
    private final DeleteAccount deleteAccount;
    private final TransactionBoundary transactionBoundary;
    private final UnverifiedAccountDays retention;
    private final Clock clock;

    UnverifiedAccountReaper(UserJdbcRepository users, DeleteAccount deleteAccount,
                            TransactionBoundary transactionBoundary,
                            UnverifiedAccountDays retention, Clock clock) {
        this.users = users;
        this.deleteAccount = deleteAccount;
        this.transactionBoundary = transactionBoundary;
        this.retention = retention;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = "1h", initialDelay = "9m")
    void reap() {
        try {
            LocalDateTime cutoff = LocalDateTime.now(clock).minusDays(retention.value());
            // one transaction per account: a sweep that fails halfway has still finished the
            // accounts it reached, and the one it stumbled on is tried again in an hour
            int removed = 0;
            for (UserEntity account : users.findUnverifiedOpenedBefore(cutoff)) {
                transactionBoundary.execute(() -> {
                    deleteAccount.execute(Email.of(account.email()));
                    return null;
                });
                removed++;
            }
            if (removed > 0) {
                LOG.info("deleted {} account(s) whose address was never verified, opened before {}",
                        removed, cutoff);
            }
        } catch (RuntimeException sweepFailed) {
            LOG.warn("the unverified-account sweep could not run: {}", sweepFailed.toString());
        }
    }
}

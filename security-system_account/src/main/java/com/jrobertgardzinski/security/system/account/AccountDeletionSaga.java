package com.jrobertgardzinski.security.system.account;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.config.account.AccountDeletionConfig;
import com.jrobertgardzinski.security.domain.entity.User;
import com.jrobertgardzinski.security.domain.port.AccountDeletionLog;
import com.jrobertgardzinski.security.domain.port.ClosureAnnouncer;
import com.jrobertgardzinski.security.domain.port.ContentPurge;
import com.jrobertgardzinski.security.domain.repository.AccountDeletionSagaStore;
import com.jrobertgardzinski.security.domain.repository.UserRepository;
import com.jrobertgardzinski.security.domain.vo.AccountClosure;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The DISTRIBUTED answer to {@link ContentPurge}: the content lives in other services, so getting
 * rid of it is a saga. Identity's side of it only — the orchestration itself lives in the portal
 * ({@code microservice-offboarding}), because the content being purged is the portal's domain.
 *
 * <p>Everything below exists because there is no shared transaction: the fact has to be announced
 * durably, the answer arrives minutes later, and a safety net has to assume the other side died.
 * A one-process deployment would implement the same port with a transaction and need none of it.
 * {@link #begin} locks the account (saga STARTED) and announces the FACT that deletion was
 * requested, with the lock; the fact ferries the leaver's purge choices without knowing their
 * vocabulary. The portal answers with ONE outcome: {@link #completePurge} (the user is deleted for
 * good, a goodbye mail goes out) or {@link #compensate} (the account unlocks, an apology goes out).
 * {@link #compensateOverdue} is the safety net for a dead orchestrator: no outcome at all in time
 * unlocks the account too. All transitions are idempotent — at-least-once delivery makes
 * duplicates a fact of life.
 *
 * <p>An identity-only deployment (no portal, e.g. security + the F1 game) does not await a purge:
 * there is no content to purge anywhere, so the account deletes immediately.
 */
public class AccountDeletionSaga implements ContentPurge {

    private final AccountDeletionSagaStore sagas;
    private final ClosureAnnouncer announcer;
    private final DeleteAccount deleteAccount;
    private final UserRepository userRepository;
    private final AccountDeletionLog log;
    private final Clock clock;
    private final AccountDeletionConfig config;

    public AccountDeletionSaga(AccountDeletionSagaStore sagas, ClosureAnnouncer announcer, DeleteAccount deleteAccount,
                               UserRepository userRepository, AccountDeletionLog log, Clock clock,
                               AccountDeletionConfig config) {
        this.sagas = sagas;
        this.announcer = announcer;
        this.deleteAccount = deleteAccount;
        this.userRepository = userRepository;
        this.log = log;
        this.clock = clock;
        this.config = config;
    }

    @Override
    public void begin(AccountClosure closure) {
        Email email = closure.target();
        if (!config.awaitPortalPurge()) {
            // identity-only deployment: no portal, no content, nothing to wait for
            deleteAccount.execute(email);
            announcer.goodbye(email);
            log.deletedImmediately(email);
            return;
        }
        UUID sagaId = UUID.randomUUID();
        if (!sagas.start(sagaId, email.value(), Instant.now(clock))) {
            // a deletion for this address is already running: the account is locked, the fact is out
            // and the portal is working on it. Announcing it again would fork a second saga whose
            // outcome settles the first one's row — so this request joins the one under way instead.
            log.alreadyUnderWay(email);
            return;
        }
        announcer.announce(closure, sagaId, userRepository.findBy(email).map(User::id).orElse(null));
    }

    /**
     * The portal announced its content purged: the user is deleted for good and a goodbye mail goes
     * out. Duplicates and strays are no-ops — the store's STARTED→COMPLETED latch admits exactly
     * one caller.
     *
     * <p>{@code sagaId} is the saga the outcome is ABOUT, echoed back by the portal; a null one
     * (an outcome from before the correlation existed, or hand-published) settles whichever
     * deletion is running for the address, as every outcome once did.
     */
    public void completePurge(UUID sagaId, Email email) {
        if (!sagas.complete(sagaId, email.value(), Instant.now(clock))) {
            // Two very different reasons to be here, and they used to share one INFO line.
            //
            // A duplicate of an outcome already applied is routine. A genuine purge confirmation
            // arriving after we COMPENSATED is not: it means the portal erased the content anyway,
            // after this service had given up, unlocked the account and sent an apology. The user
            // keeps their account and loses every meme, comment and collection they had.
            if (sagas.lastSagaWasCompensated(email.value())) {
                log.erasedAfterCompensation(email);
            } else {
                log.strayConfirmation(email);
            }
            return;
        }
        deleteAccount.execute(email);
        announcer.goodbye(email);
        log.completed(email);
    }

    /**
     * The portal announced the purge FAILED: the account unlocks and the user is apologised to.
     *
     * <p>{@code reserved} names the participants that DID act before the failure. The portal has
     * always disclosed that list — it is the difference between "nothing happened, try again" and
     * "your memes are hidden, your comments are not" — and the apology still says only that the
     * deletion failed; putting the list in front of the USER means a new field on the mail request
     * and a wider pact, which belongs with that decision.
     */
    public void compensate(UUID sagaId, Email email, List<String> reserved) {
        if (!sagas.compensate(sagaId, email.value(), Instant.now(clock))) {
            log.strayFailure(email);
            return;
        }
        userRepository.clearPendingDeletion(email);
        announcer.apology(email);
        log.compensated(email, reserved);
    }

    /** The safety net: no outcome AT ALL in time (a dead orchestrator) unlocks the account too. */
    public void compensateOverdue() {
        Instant now = Instant.now(clock);
        for (String overdue : sagas.compensateOverdue(now.minus(config.purgeTimeout()), now)) {
            Email email = Email.of(overdue);
            userRepository.clearPendingDeletion(email);
            announcer.apology(email);
            log.compensatedOverdue(email, config.purgeTimeout());
        }
    }
}

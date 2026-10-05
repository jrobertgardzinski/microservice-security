package com.jrobertgardzinski.security.application.account;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.email.domain.InvalidEmailException;
import com.jrobertgardzinski.email.domain.NormalizedEmail;
import com.jrobertgardzinski.security.application.TransactionBoundary;
import com.jrobertgardzinski.security.domain.core.UserRepository;
import com.jrobertgardzinski.security.domain.account.AccountClosure;
import com.jrobertgardzinski.security.domain.account.PurgeChoices;
import com.jrobertgardzinski.security.domain.core.Role;
import com.jrobertgardzinski.security.domain.mfa.StepUpAction;
import com.jrobertgardzinski.security.system.account.AccountDeletionSaga;
import com.jrobertgardzinski.security.system.account.StartAccountDeletion;
import com.jrobertgardzinski.security.system.core.RequireRole;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * The ONE door out of an account, for the person whose account it is and for an administrator
 * closing somebody else's. Which of the two it is comes from the request itself — the address
 * asked for against the caller's — and everything else follows from that comparison: what must be
 * proven, and whether the caller may say what happens to the content.
 *
 * <p>Closing is a saga: {@link StartAccountDeletion} locks the account at once (sessions revoked,
 * sign-in refused) and asks the content services to purge; their outcome — not this request —
 * settles it, through {@link #confirmPurge} or {@link #failPurge}, and {@link #compensateOverdue}
 * settles the ones nobody answered for.
 */
public final class AccountDeletionService {

    private final StartAccountDeletion startAccountDeletion;
    private final AccountDeletionSaga saga;
    private final UserRepository users;
    private final RequireRole requireRole;
    private final TransactionBoundary transactionBoundary;

    public AccountDeletionService(StartAccountDeletion startAccountDeletion, AccountDeletionSaga saga,
                                  UserRepository users, RequireRole requireRole,
                                  TransactionBoundary transactionBoundary) {
        this.startAccountDeletion = startAccountDeletion;
        this.saga = saga;
        this.users = users;
        this.requireRole = requireRole;
        this.transactionBoundary = transactionBoundary;
    }

    /**
     * @param ownStepUp   the caller has stepped up for {@link StepUpAction#DELETE_ACCOUNT}
     * @param adminStepUp the caller has stepped up for {@link StepUpAction#ADMIN_DELETE_ACCOUNT}
     * @param purge       an administrator's rule per content axis, opaque here; null for none
     */
    public Closure start(Email caller, String account, Map<String, String> purge,
                         BooleanSupplier ownStepUp, BooleanSupplier adminStepUp) {
        Email target;
        try {
            target = Email.of(account);
        } catch (InvalidEmailException notAnAddress) {
            return new Closure.InvalidEmail();
        }
        // the same account, by the same rule registration uses to decide two addresses are one
        // person: a caller whose token spells their address differently is still closing their OWN
        // account, and must not be sent down the administrator's road. On that road the CALLER's
        // address is what travels, never the one asked for: everything downstream — the lock, the
        // saga row, the fact's key — addresses the user by the exact string the account is stored
        // under, which is the one the token carries.
        return NormalizedEmail.of(target).equals(NormalizedEmail.of(caller))
                ? closeOwnAccount(caller, ownStepUp)
                : closeSomebodyElses(caller, target, purge, adminStepUp);
    }

    /**
     * A request to be forgotten states no conditions, so there are none to read: everything the
     * person ever posted goes. This IS the right to erasure, and the grounds on which content may
     * survive it are enumerated by law. An older client that still sends a purge map is answered
     * with the deletion it asked for — refusing it would leave that person unable to close their
     * account over a field that could not have changed the outcome anyway.
     */
    private Closure closeOwnAccount(Email caller, BooleanSupplier stepUp) {
        // irreversible: a live session is not enough, the thief of one would have to pass the chain too
        if (!stepUp.getAsBoolean()) {
            return new Closure.StepUpRequired(StepUpAction.DELETE_ACCOUNT);
        }
        return started(AccountClosure.requestedByOwner(caller), new Closure.StartedByOwner());
    }

    private Closure closeSomebodyElses(Email caller, Email target, Map<String, String> purge, BooleanSupplier stepUp) {
        var role = requireRole.check(caller, Role.ADMIN);
        if (!role.errorCodes().isEmpty()) {
            return new Closure.NotPermitted(role.errorCodes().getFirst());
        }
        // AFTER the role check, so somebody who may not do this at all learns only that. A stolen
        // admin session must prove itself again before destroying a stranger's account.
        if (!stepUp.getAsBoolean()) {
            return new Closure.StepUpRequired(StepUpAction.ADMIN_DELETE_ACCOUNT);
        }
        PurgeChoices choices;
        try {
            choices = purge == null || purge.isEmpty() ? PurgeChoices.serviceDefaults() : new PurgeChoices(purge);
        } catch (IllegalArgumentException oversized) {
            // an over-large or over-wide purge map is the caller's mistake — refused here so it never
            // becomes an unpublishable outbox row (see PurgeChoices bounds)
            return new Closure.InvalidPurgeChoices();
        }
        // asked before the lock, and only on this road: starting a saga for an address nobody holds
        // would announce a deletion fact the portal then purges content for, and answer "started" to
        // an administrator who mistyped. (On the other road the caller's own account exists by
        // definition — their token was issued for it.)
        if (users.findBy(target).isEmpty()) {
            return new Closure.NoSuchUser();
        }
        return started(AccountClosure.requestedByAdministrator(target, choices),
                new Closure.StartedByAdministrator(target, caller));
    }

    /**
     * The portal confirmed its purge: the account goes for good. {@code sagaId} is the deletion the
     * outcome names, null when it names none — then whichever deletion runs for the address is
     * settled.
     */
    public Settlement confirmPurge(UUID sagaId, String leaver) {
        return settle(leaver, email -> saga.completePurge(sagaId, email));
    }

    /**
     * The portal reported its purge failed: the account unlocks.
     *
     * @param reserved the participants that had already reserved the content before the failure
     */
    public Settlement failPurge(UUID sagaId, String leaver, List<String> reserved) {
        return settle(leaver, email -> saga.compensate(sagaId, email, reserved));
    }

    /** The safety net: every deletion nobody answered for in time unlocks. */
    public void compensateOverdue() {
        transactionBoundary.execute(() -> {
            saga.compensateOverdue();
            return null;
        });
    }

    private Settlement settle(String leaver, Consumer<Email> settlement) {
        Email email;
        try {
            email = Email.of(leaver);
        } catch (IllegalArgumentException unreadable) {
            // no deletion was ever started for an address the domain cannot read
            return new Settlement.UnreadableAddress();
        }
        transactionBoundary.execute(() -> {
            settlement.accept(email);
            return null;
        });
        return new Settlement.Settled();
    }

    private Closure started(AccountClosure closure, Closure outcome) {
        transactionBoundary.execute(() -> {
            startAccountDeletion.execute(closure);
            return null;
        });
        return outcome;
    }

    public sealed interface Closure {

        record StartedByOwner() implements Closure {}

        /**
         * Both addresses, for the audit line — the only place they ever meet: the fact on the wire
         * says ADMIN and not WHICH admin.
         */
        record StartedByAdministrator(Email account, Email administrator) implements Closure {}

        record InvalidEmail() implements Closure {}

        /** Somebody else's account, and the caller lacks the role; {@code code} is the rule's own. */
        record NotPermitted(String code) implements Closure {}

        record StepUpRequired(StepUpAction action) implements Closure {}

        record InvalidPurgeChoices() implements Closure {}

        record NoSuchUser() implements Closure {}
    }

    public sealed interface Settlement {

        /** Handed to the saga, which decides whether it settles anything or is a duplicate. */
        record Settled() implements Settlement {}

        record UnreadableAddress() implements Settlement {}
    }
}

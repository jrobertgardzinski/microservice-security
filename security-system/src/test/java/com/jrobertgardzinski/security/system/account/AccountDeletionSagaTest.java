package com.jrobertgardzinski.security.system.account;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.config.account.AccountDeletionConfig;
import com.jrobertgardzinski.security.domain.account.AccountDeletionLog;
import com.jrobertgardzinski.security.domain.account.ClosureAnnouncer;
import com.jrobertgardzinski.security.domain.account.AccountDeletionSagaStore;
import com.jrobertgardzinski.security.domain.core.UserRepository;
import com.jrobertgardzinski.security.domain.account.AccountClosure;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import net.jqwik.api.Example;
import net.jqwik.api.Label;
import net.jqwik.api.lifecycle.BeforeTry;
import org.mockito.Mockito;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Epic("Use case")
@Feature("Delete account")
class AccountDeletionSagaTest {

    private static final Email LEAVER = Email.of("leaver@example.com");

    private AccountDeletionSagaStore sagas;
    private ClosureAnnouncer announcer;
    private DeleteAccount deleteAccount;
    private UserRepository users;
    private AccountDeletionLog log;

    @BeforeTry
    void init() {
        sagas = Mockito.mock(AccountDeletionSagaStore.class);
        announcer = Mockito.mock(ClosureAnnouncer.class);
        deleteAccount = Mockito.mock(DeleteAccount.class);
        users = Mockito.mock(UserRepository.class);
        log = Mockito.mock(AccountDeletionLog.class);
        Mockito.when(users.findBy(LEAVER)).thenReturn(Optional.empty());
    }

    private AccountDeletionSaga saga(boolean awaitPortalPurge) {
        return new AccountDeletionSaga(sagas, announcer, deleteAccount, users, log, Clock.systemUTC(),
                new AccountDeletionConfig(Duration.ofMinutes(12), awaitPortalPurge));
    }

    @Example
    @Label("A deletion locks the account and announces the fact, and deletes nothing yet")
    void a_deletion_is_announced_not_done() {
        Mockito.when(sagas.start(Mockito.any(), Mockito.eq(LEAVER.value()), Mockito.any())).thenReturn(true);

        saga(true).begin(AccountClosure.requestedByOwner(LEAVER));

        Mockito.verify(announcer).announce(Mockito.any(), Mockito.any(), Mockito.isNull());
        Mockito.verifyNoInteractions(deleteAccount);
    }

    @Example
    @Label("A second request while one runs joins it, and announces nothing")
    void a_running_deletion_is_joined() {
        Mockito.when(sagas.start(Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(false);

        saga(true).begin(AccountClosure.requestedByOwner(LEAVER));

        Mockito.verify(log).alreadyUnderWay(LEAVER);
        Mockito.verifyNoInteractions(announcer);
    }

    @Example
    @Label("With no portal to wait for, the account goes at once")
    void identity_only_deletes_at_once() {
        saga(false).begin(AccountClosure.requestedByOwner(LEAVER));

        Mockito.verify(deleteAccount).execute(LEAVER);
        Mockito.verify(announcer).goodbye(LEAVER);
        Mockito.verifyNoInteractions(sagas);
    }

    @Example
    @Label("A confirmation after the deletion was given up on is an alarm, not a deletion")
    void a_confirmation_after_compensation_raises_the_alarm() {
        Mockito.when(sagas.complete(Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(false);
        Mockito.when(sagas.lastSagaWasCompensated(LEAVER.value())).thenReturn(true);

        saga(true).completePurge(UUID.randomUUID(), LEAVER);

        Mockito.verify(log).erasedAfterCompensation(LEAVER);
        Mockito.verifyNoInteractions(deleteAccount, announcer);
    }

    @Example
    @Label("A failed purge unlocks the account and apologises")
    void a_failed_purge_unlocks_and_apologises() {
        Mockito.when(sagas.compensate(Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(true);

        saga(true).compensate(UUID.randomUUID(), LEAVER, List.of("memes"));

        Mockito.verify(users).clearPendingDeletion(LEAVER);
        Mockito.verify(announcer).apology(LEAVER);
        Mockito.verify(log).compensated(LEAVER, List.of("memes"));
    }
}

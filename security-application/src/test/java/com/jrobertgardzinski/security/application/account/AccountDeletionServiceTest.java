package com.jrobertgardzinski.security.application.account;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.password.domain.HashedPassword;
import com.jrobertgardzinski.security.application.TransactionBoundary;
import com.jrobertgardzinski.security.domain.core.User;
import com.jrobertgardzinski.security.domain.core.FakeUserRepository;
import com.jrobertgardzinski.security.domain.core.Role;
import com.jrobertgardzinski.security.domain.core.StepUpAction;
import com.jrobertgardzinski.security.system.account.AccountDeletionSaga;
import com.jrobertgardzinski.security.system.account.StartAccountDeletion;
import com.jrobertgardzinski.security.system.core.BootstrapAdmins;
import com.jrobertgardzinski.security.system.core.RequireRole;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Set;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class AccountDeletionServiceTest {

    private static final Email OWNER = Email.of("owner@example.com");
    private static final Email ADMIN = Email.of("admin@example.com");

    private final StartAccountDeletion startAccountDeletion = Mockito.mock(StartAccountDeletion.class);
    private final FakeUserRepository users = new FakeUserRepository();
    private final AccountDeletionSaga saga = Mockito.mock(AccountDeletionSaga.class);
    private final AccountDeletionService service = new AccountDeletionService(startAccountDeletion, saga, users,
            new RequireRole(BootstrapAdmins.of(Set.of(ADMIN.value())), email -> Set.of(Role.USER)),
            new TransactionBoundary() {
                @Override
                public <T> T execute(Supplier<T> work) {
                    return work.get();
                }
            });

    @Test
    void the_same_account_spelled_differently_takes_the_owners_road() {
        AccountDeletionService.Closure closure =
                service.start(OWNER, "Owner@Example.com", null, () -> false, () -> true);

        // the owner's step-up was asked, not the administrator's
        assertEquals(new AccountDeletionService.Closure.StepUpRequired(StepUpAction.DELETE_ACCOUNT), closure);
    }

    @Test
    void somebody_elses_account_needs_the_role_before_any_step_up() {
        assertInstanceOf(AccountDeletionService.Closure.NotPermitted.class,
                service.start(OWNER, "stranger@example.com", null, () -> true, () -> {
                    throw new AssertionError("the step-up was asked of somebody who may not do this at all");
                }));
    }

    @Test
    void an_administrator_who_mistyped_starts_no_saga() {
        assertInstanceOf(AccountDeletionService.Closure.NoSuchUser.class,
                service.start(ADMIN, "nobody@example.com", null, () -> false, () -> true));

        Mockito.verifyNoInteractions(startAccountDeletion);
    }

    @Test
    void an_administrator_closing_an_existing_account_starts_the_saga() {
        users.save(new User(OWNER, new HashedPassword("seed-hash")));

        assertInstanceOf(AccountDeletionService.Closure.StartedByAdministrator.class,
                service.start(ADMIN, OWNER.value(), null, () -> false, () -> true));

        Mockito.verify(startAccountDeletion).execute(Mockito.any());
    }

    @Test
    void an_outcome_for_an_address_nobody_could_hold_reaches_no_saga() {
        assertInstanceOf(AccountDeletionService.Settlement.UnreadableAddress.class,
                service.confirmPurge(null, "null"));

        Mockito.verifyNoInteractions(saga);
    }

    @Test
    void an_outcome_reaches_the_saga_on_the_domain() {
        java.util.UUID sagaId = java.util.UUID.randomUUID();

        service.failPurge(sagaId, OWNER.value(), java.util.List.of("memes"));

        Mockito.verify(saga).compensate(sagaId, OWNER, java.util.List.of("memes"));
    }
}

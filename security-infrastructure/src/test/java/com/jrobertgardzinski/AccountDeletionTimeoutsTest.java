package com.jrobertgardzinski;

import io.micronaut.scheduling.annotation.Scheduled;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * The safety net behind the deletion saga, and the only thing that ever frees an account whose
 * portal outcome never arrived.
 *
 * <p>It is switched off in the {@code test} environment (the Gherkin steps drive the orchestrator
 * directly, with the steerable clock deciding what "overdue" means) — which is exactly why nothing
 * exercised it at all: no suite boots the environment it lives in. So it is driven here as the
 * plain object it is.
 *
 * <p>Two claims, both about the ways a scheduler quietly stops mattering: the sweep must run inside
 * a transaction (it unlocks an account and mails an apology — half of that committed is worse than
 * neither), and a failed tick must not be the last one.
 */
@Epic("Account deletion")
@Feature("Timeouts")
class AccountDeletionTimeoutsTest {

    @Test
    @DisplayName("the sweep runs inside the transaction boundary")
    void the_sweep_is_transactional() {
        List<String> order = new ArrayList<>();
        AccountDeletionOrchestrator orchestrator = org.mockito.Mockito.mock(AccountDeletionOrchestrator.class);
        org.mockito.Mockito.doAnswer(invocation -> order.add("swept"))
                .when(orchestrator).compensateOverdue();

        new AccountDeletionTimeouts(orchestrator, recording(order)).tick();

        assertThat(order)
                .as("unlocking the account and mailing the apology are one unit of work; a sweep"
                        + " outside the boundary can commit half of it")
                .containsExactly("transaction opened", "swept", "transaction closed");
    }

    @Test
    @DisplayName("a tick that fails is not the last tick")
    void a_failed_sweep_does_not_end_the_schedule() {
        AccountDeletionOrchestrator orchestrator = org.mockito.Mockito.mock(AccountDeletionOrchestrator.class);
        org.mockito.Mockito.doThrow(new IllegalStateException("the database is away"))
                .when(orchestrator).compensateOverdue();

        assertThatCode(() -> new AccountDeletionTimeouts(orchestrator, recording(new ArrayList<>())).tick())
                .as("this is the ONLY thing that unlocks an account nobody answered for; letting"
                        + " the exception out leaves those people locked out with nothing coming")
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("and it is actually scheduled")
    void the_sweep_is_on_a_schedule() throws Exception {
        Scheduled schedule = AccountDeletionTimeouts.class
                .getDeclaredMethod("tick").getAnnotation(Scheduled.class);

        assertThat(schedule)
                .as("a safety net nothing calls is not a safety net")
                .isNotNull();
        assertThat(schedule.fixedDelay()).isNotBlank();
    }

    private static TransactionBoundary recording(List<String> order) {
        return new TransactionBoundary() {
            @Override
            public <T> T execute(Supplier<T> work) {
                order.add("transaction opened");
                T result = work.get();
                order.add("transaction closed");
                return result;
            }
        };
    }
}

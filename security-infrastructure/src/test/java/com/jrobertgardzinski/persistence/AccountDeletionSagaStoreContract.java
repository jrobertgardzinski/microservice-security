package com.jrobertgardzinski.persistence;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The behaviour BOTH {@link AccountDeletionSagaStore} implementations must show (poz. 26).
 *
 * <p>They used to differ, and the difference was not cosmetic: the JDBC store settles a saga with
 * {@code UPDATE ... WHERE email = ? AND state = 'STARTED'} — every matching row — while the
 * in-memory double changed only the first one it found. Nothing anywhere stopped a second STARTED
 * row from existing, so the tests rode a store that behaved unlike the one in production precisely
 * in the situation that hurts: one portal outcome settling two deletions of one address.
 *
 * <p>Running the same assertions through both stores is the point. If one of them drifts again,
 * this fails for that one.
 */
final class AccountDeletionSagaStoreContract {

    private AccountDeletionSagaStoreContract() {
    }

    static void oneRunningSagaPerAddress(AccountDeletionSagaStore store, String leaver, String other) {
        Instant t0 = Instant.parse("2026-07-30T10:00:00Z");

        assertTrue(store.start(UUID.randomUUID(), leaver, t0),
                "the first request opens the saga");
        assertFalse(store.start(UUID.randomUUID(), leaver, t0.plusSeconds(30)),
                "a second request must NOT open a second saga while one is running");
        assertTrue(store.start(UUID.randomUUID(), other, t0.plusSeconds(30)),
                "another person's deletion is none of this address's business");

        assertTrue(store.complete(leaver, t0.plusSeconds(60)),
                "the portal's outcome settles the running saga");
        assertFalse(store.complete(leaver, t0.plusSeconds(61)),
                "and settles it exactly once — a duplicate outcome latches nothing");
        assertTrue(store.compensate(other, t0.plusSeconds(62)),
                "the other person's saga was still running: one address's outcome settled only its own");

        assertTrue(store.start(UUID.randomUUID(), leaver, t0.plusSeconds(90)),
                "a settled saga releases the address, so a later deletion can be requested again");
    }
}

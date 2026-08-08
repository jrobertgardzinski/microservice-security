package com.jrobertgardzinski.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The database-less store answers the same questions as Postgres — see the contract's javadoc. */
class InMemoryAccountDeletionSagaStoreTest {

    @Test
    @DisplayName("the database-less store admits one running deletion per address, like Postgres")
    void one_running_saga_per_address() {
        AccountDeletionSagaStoreContract.oneRunningSagaPerAddress(
                new InMemoryAccountDeletionSagaStore(java.time.Clock.systemUTC()), "leaver@example.com", "other@example.com");
    }
}

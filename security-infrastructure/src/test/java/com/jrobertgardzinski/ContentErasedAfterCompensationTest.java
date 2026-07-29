package com.jrobertgardzinski;

import com.jrobertgardzinski.persistence.AccountDeletionSagaStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The one outcome this service must never absorb quietly.
 *
 * <p>When a purge confirmation arrives and no saga is running, there are two possible reasons and
 * they are worlds apart. A duplicate of something already applied is routine. A GENUINE
 * confirmation arriving after this service compensated means the portal erased the content anyway,
 * after we had given up, unlocked the account and sent an apology: the user keeps their account and
 * loses every meme, comment and collection they ever had. Both used to end on the same INFO line.
 *
 * <p>It is reachable without anything exotic. Security gives the portal five minutes
 * ({@code account-deletion.purge-timeout}); the portal's own retry budget is an independent dial in
 * a different repository and tolerates far longer. Whenever the portal comes back after security
 * gave up, this is the path.
 */
class ContentErasedAfterCompensationTest {

    /** The store's own semantics, without a database: a latch that only closes once. */
    private static final class LatchingStore implements AccountDeletionSagaStore {
        private String state = "STARTED";

        public void start(UUID sagaId, String email, Instant at) {
            state = "STARTED";
        }

        public boolean complete(String email, Instant at) {
            if (!"STARTED".equals(state)) {
                return false;
            }
            state = "COMPLETED";
            return true;
        }

        public boolean compensate(String email, Instant at) {
            if (!"STARTED".equals(state)) {
                return false;
            }
            state = "COMPENSATED";
            return true;
        }

        public List<String> compensateOverdue(Instant cutoff, Instant at) {
            return compensate("leaver@example.com", at) ? List.of("leaver@example.com") : List.of();
        }

        public boolean lastSagaWasCompensated(String email) {
            return "COMPENSATED".equals(state);
        }
    }

    @Test
    @DisplayName("a success after a compensation is distinguishable from a harmless duplicate")
    void the_store_tells_the_catastrophe_from_the_duplicate() {
        LatchingStore afterCompensation = new LatchingStore();
        afterCompensation.compensate("leaver@example.com", Instant.now());

        // the portal comes back and confirms the purge it eventually completed
        assertFalse(afterCompensation.complete("leaver@example.com", Instant.now()),
                "the latch is closed — nothing to complete");
        assertTrue(afterCompensation.lastSagaWasCompensated("leaver@example.com"),
                "and THIS is what makes it an alarm rather than an INFO line: we already gave up,"
                        + " unlocked the account and apologised, and the content is gone anyway");
    }

    @Test
    @DisplayName("a duplicate of an already-completed deletion stays the routine case it is")
    void a_duplicate_completion_is_not_an_alarm() {
        LatchingStore afterCompletion = new LatchingStore();
        afterCompletion.complete("leaver@example.com", Instant.now());

        assertFalse(afterCompletion.complete("leaver@example.com", Instant.now()));
        assertFalse(afterCompletion.lastSagaWasCompensated("leaver@example.com"),
                "nothing went wrong here — the deletion finished, and this is its echo");
    }
}

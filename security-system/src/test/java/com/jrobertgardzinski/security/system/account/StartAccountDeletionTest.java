package com.jrobertgardzinski.security.system.account;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.port.ContentPurge;
import com.jrobertgardzinski.security.domain.repository.AuthorizationDataRepository;
import com.jrobertgardzinski.security.domain.repository.UserRepository;
import com.jrobertgardzinski.security.domain.vo.AccountClosure;
import com.jrobertgardzinski.security.domain.vo.DeletionInitiator;
import com.jrobertgardzinski.security.domain.vo.PurgeChoices;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Asking for an account to be closed locks it AT ONCE, and only then asks the content to go.
 *
 * <p>The use case had no test of its own: the browser scenario checks that the CALLING access token
 * stops working, which the same request would satisfy by revoking one session and nothing else. The
 * three things that matter are the ones a caller cannot see — every session goes, the account is
 * marked so sign-in refuses it, and the purge is asked for after both, not before. A purge that
 * starts while the account is still usable is a window in which the owner is signing in to content
 * that is being deleted underneath them.
 */
@Epic("Use case")
@Feature("Start account deletion")
class StartAccountDeletionTest {

    private static final Email LEAVER = Email.of("leaver@example.com");

    private UserRepository users;
    private AuthorizationDataRepository sessions;
    private ContentPurge purge;
    private StartAccountDeletion startAccountDeletion;

    @BeforeEach
    void init() {
        users = Mockito.mock(UserRepository.class);
        sessions = Mockito.mock(AuthorizationDataRepository.class);
        purge = Mockito.mock(ContentPurge.class);
        startAccountDeletion = new StartAccountDeletion(users, sessions, purge);
    }

    @Test
    @DisplayName("the account is locked before the purge is asked for, not after")
    void the_lock_comes_first() {
        startAccountDeletion.execute(AccountClosure.requestedByOwner(LEAVER));

        InOrder inOrder = Mockito.inOrder(sessions, users, purge);
        inOrder.verify(sessions).revokeAllSessions(LEAVER);
        inOrder.verify(users).markPendingDeletion(LEAVER);
        inOrder.verify(purge).begin(Mockito.any());
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    @DisplayName("EVERY session goes, not just the one that asked")
    void it_is_not_a_logout() {
        startAccountDeletion.execute(AccountClosure.requestedByOwner(LEAVER));

        // revokeAllSessions is the whole claim here: the scenario that watches the caller's own
        // token would pass just as happily if this revoked that one session and left the phone
        // signed in to an account that is being deleted
        Mockito.verify(sessions).revokeAllSessions(LEAVER);
        Mockito.verify(sessions, Mockito.never()).revokeFamily(Mockito.any());
    }

    @Test
    @DisplayName("the closure reaches the purge exactly as it was stated")
    void the_purge_is_told_whose_request_this_is() {
        PurgeChoices adminChoices = new PurgeChoices(Map.of("memes", "keep-popular"));

        startAccountDeletion.execute(AccountClosure.requestedByAdministrator(LEAVER, adminChoices));

        ArgumentCaptor<AccountClosure> asked = ArgumentCaptor.forClass(AccountClosure.class);
        Mockito.verify(purge).begin(asked.capture());
        assertThat(asked.getValue().target()).isEqualTo(LEAVER);
        assertThat(asked.getValue().requestedBy())
                .as("the participants answer an owner's erasure differently from an administrator's"
                        + " decision — the difference is a legal basis, not a preference")
                .isEqualTo(DeletionInitiator.ADMIN);
        assertThat(asked.getValue().choices()).isEqualTo(adminChoices);
    }

    @Test
    @DisplayName("an owner's own request carries no conditions, whatever the caller sent")
    void an_owners_request_carries_no_conditions() {
        startAccountDeletion.execute(
                new AccountClosure(LEAVER, DeletionInitiator.SELF, new PurgeChoices(Map.of("memes", "keep-popular"))));

        ArgumentCaptor<AccountClosure> asked = ArgumentCaptor.forClass(AccountClosure.class);
        Mockito.verify(purge).begin(asked.capture());
        assertThat(asked.getValue().choices())
                .as("the exceptions to the right to erasure are enumerated by law, and 'the"
                        + " community up-voted it' is not among them")
                .isEqualTo(PurgeChoices.serviceDefaults());
    }
}

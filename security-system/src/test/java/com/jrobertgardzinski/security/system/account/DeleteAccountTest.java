package com.jrobertgardzinski.security.system.account;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.repository.AuthorizationDataRepository;
import com.jrobertgardzinski.security.domain.repository.UserRepository;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import net.jqwik.api.Example;
import net.jqwik.api.Label;
import net.jqwik.api.lifecycle.BeforeTry;
import org.mockito.InOrder;
import org.mockito.Mockito;

@Epic("Use case")
@Feature("Delete account")
class DeleteAccountTest {

    private static final Email EMAIL = Email.of("user@example.com");

    private UserRepository userRepository;
    private AuthorizationDataRepository authorizationDataRepository;
    private DeleteAccount deleteAccount;

    @BeforeTry
    void init() {
        userRepository = Mockito.mock(UserRepository.class);
        authorizationDataRepository = Mockito.mock(AuthorizationDataRepository.class);
        deleteAccount = new DeleteAccount(userRepository, authorizationDataRepository);
    }

    @Example
    @Label("Closing the account revokes the sessions and deletes the user")
    void revokes_sessions_and_deletes_the_user() {
        deleteAccount.execute(EMAIL);

        InOrder inOrder = Mockito.inOrder(authorizationDataRepository, userRepository);
        inOrder.verify(authorizationDataRepository).revokeAllSessions(EMAIL);
        inOrder.verify(userRepository).deleteByEmail(EMAIL);
    }
}

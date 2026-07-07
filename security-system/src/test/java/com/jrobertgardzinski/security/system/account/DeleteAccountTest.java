package com.jrobertgardzinski.security.system.account;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.repository.AuthorizationDataRepository;
import com.jrobertgardzinski.security.domain.repository.EnrolledFactorRepository;
import com.jrobertgardzinski.security.domain.repository.FederatedIdentityRepository;
import com.jrobertgardzinski.security.domain.repository.RecoveryCodeRepository;
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
    private EnrolledFactorRepository enrolledFactorRepository;
    private RecoveryCodeRepository recoveryCodeRepository;
    private FederatedIdentityRepository federatedIdentityRepository;
    private DeleteAccount deleteAccount;

    @BeforeTry
    void init() {
        userRepository = Mockito.mock(UserRepository.class);
        authorizationDataRepository = Mockito.mock(AuthorizationDataRepository.class);
        enrolledFactorRepository = Mockito.mock(EnrolledFactorRepository.class);
        recoveryCodeRepository = Mockito.mock(RecoveryCodeRepository.class);
        federatedIdentityRepository = Mockito.mock(FederatedIdentityRepository.class);
        deleteAccount = new DeleteAccount(userRepository, authorizationDataRepository,
                enrolledFactorRepository, recoveryCodeRepository, federatedIdentityRepository);
    }

    @Example
    @Label("Closing the account revokes sessions, wipes MFA factors and recovery codes, then deletes the user")
    void wipes_everything_then_deletes_the_user() {
        deleteAccount.execute(EMAIL);

        // the secrets (factor material, recovery-code hashes) must be gone BEFORE the user row is —
        // and by the time the row is deleted, nothing of the account survives
        InOrder inOrder = Mockito.inOrder(authorizationDataRepository, enrolledFactorRepository,
                recoveryCodeRepository, federatedIdentityRepository, userRepository);
        inOrder.verify(authorizationDataRepository).revokeAllSessions(EMAIL);
        inOrder.verify(enrolledFactorRepository).removeAll(EMAIL);
        inOrder.verify(recoveryCodeRepository).removeAll(EMAIL);
        inOrder.verify(federatedIdentityRepository).unlinkAll(EMAIL);
        inOrder.verify(userRepository).deleteByEmail(EMAIL);
    }
}

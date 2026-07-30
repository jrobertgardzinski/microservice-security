package com.jrobertgardzinski.security.system.account;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.repository.AuthorizationDataRepository;
import com.jrobertgardzinski.security.domain.repository.EmailChangeRepository;
import com.jrobertgardzinski.security.domain.repository.EmailVerificationRepository;
import com.jrobertgardzinski.security.domain.repository.EnrolledFactorRepository;
import com.jrobertgardzinski.security.domain.repository.FederatedIdentityRepository;
import com.jrobertgardzinski.security.domain.repository.PasswordResetRepository;
import com.jrobertgardzinski.security.domain.repository.PasswordlessAccountRepository;
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
    private EmailVerificationRepository emailVerificationRepository;
    private PasswordResetRepository passwordResetRepository;
    private EmailChangeRepository emailChangeRepository;
    private PasswordlessAccountRepository passwordlessAccountRepository;
    private DeleteAccount deleteAccount;

    @BeforeTry
    void init() {
        userRepository = Mockito.mock(UserRepository.class);
        authorizationDataRepository = Mockito.mock(AuthorizationDataRepository.class);
        enrolledFactorRepository = Mockito.mock(EnrolledFactorRepository.class);
        recoveryCodeRepository = Mockito.mock(RecoveryCodeRepository.class);
        federatedIdentityRepository = Mockito.mock(FederatedIdentityRepository.class);
        emailVerificationRepository = Mockito.mock(EmailVerificationRepository.class);
        passwordResetRepository = Mockito.mock(PasswordResetRepository.class);
        emailChangeRepository = Mockito.mock(EmailChangeRepository.class);
        passwordlessAccountRepository = Mockito.mock(PasswordlessAccountRepository.class);
        deleteAccount = new DeleteAccount(userRepository, authorizationDataRepository,
                enrolledFactorRepository, recoveryCodeRepository, federatedIdentityRepository,
                emailVerificationRepository, passwordResetRepository, emailChangeRepository,
                passwordlessAccountRepository);
    }

    @Example
    @Label("Closing the account revokes sessions, wipes MFA factors and recovery codes, then deletes the user")
    void wipes_everything_then_deletes_the_user() {
        deleteAccount.execute(EMAIL);

        // the secrets (factor material, recovery-code hashes) must be gone BEFORE the user row is
        InOrder inOrder = Mockito.inOrder(authorizationDataRepository, enrolledFactorRepository,
                recoveryCodeRepository, federatedIdentityRepository, userRepository);
        inOrder.verify(authorizationDataRepository).revokeAllSessions(EMAIL);
        inOrder.verify(enrolledFactorRepository).removeAll(EMAIL);
        inOrder.verify(recoveryCodeRepository).removeAll(EMAIL);
        inOrder.verify(federatedIdentityRepository).unlinkAll(EMAIL);
        inOrder.verify(userRepository).deleteByEmail(EMAIL);
    }

    @Example
    @Label("Nothing keyed by the address survives the account: the pending-token tables are purged too")
    void every_table_keyed_by_the_address_is_purged() {
        // The earlier version of this test claimed "by the time the row is deleted, nothing of the
        // account survives" in a COMMENT, while checking five repositories out of nine — and the four
        // it did not check were not even constructor arguments, so the claim could not be false in a
        // way the test could see. Four tables keyed by the address really did outlive the account, and
        // a reset link left in one of them set the password of whoever registered the freed address
        // next. The promise is now checked, not narrated — and each purge happens BEFORE the user row
        // goes, so a crash mid-way can never leave secrets behind with no account pointing at them.
        deleteAccount.execute(EMAIL);

        InOrder inOrder = Mockito.inOrder(passwordResetRepository, emailChangeRepository,
                emailVerificationRepository, passwordlessAccountRepository, userRepository);
        inOrder.verify(passwordResetRepository).purge(EMAIL);
        inOrder.verify(emailChangeRepository).purge(EMAIL);
        inOrder.verify(emailVerificationRepository).purge(EMAIL);
        inOrder.verify(passwordlessAccountRepository).purge(EMAIL);
        inOrder.verify(userRepository).deleteByEmail(EMAIL);
    }
}

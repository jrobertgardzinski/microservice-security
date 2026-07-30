package com.jrobertgardzinski.security.system.account;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.repository.EmailChangeRepository;
import com.jrobertgardzinski.security.domain.repository.EmailVerificationRepository;
import com.jrobertgardzinski.security.domain.repository.EnrolledFactorRepository;
import com.jrobertgardzinski.security.domain.repository.PasswordResetRepository;
import com.jrobertgardzinski.security.domain.repository.PasswordlessAccountRepository;
import com.jrobertgardzinski.security.domain.repository.RecoveryCodeRepository;
import com.jrobertgardzinski.security.domain.repository.UserRepository;
import com.jrobertgardzinski.security.domain.vo.EmailChange;
import com.jrobertgardzinski.security.domain.vo.token.VerificationToken;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import net.jqwik.api.Example;
import net.jqwik.api.Label;
import net.jqwik.api.lifecycle.BeforeTry;
import org.mockito.Mockito;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@Epic("Use case")
@Feature("Change email")
class ConfirmEmailChangeTest {

    private static final VerificationToken TOKEN = new VerificationToken("change-token");
    private static final Email OLD = Email.of("user@example.com");
    private static final Email NEW = Email.of("new@example.com");

    private EmailChangeRepository emailChangeRepository;
    private UserRepository userRepository;
    private EmailVerificationRepository emailVerificationRepository;
    private com.jrobertgardzinski.security.domain.repository.FederatedIdentityRepository federatedIdentityRepository;
    private EnrolledFactorRepository enrolledFactorRepository;
    private RecoveryCodeRepository recoveryCodeRepository;
    private PasswordlessAccountRepository passwordlessAccountRepository;
    private PasswordResetRepository passwordResetRepository;
    private ConfirmEmailChange confirmEmailChange;

    @BeforeTry
    void init() {
        emailChangeRepository = Mockito.mock(EmailChangeRepository.class);
        userRepository = Mockito.mock(UserRepository.class);
        emailVerificationRepository = Mockito.mock(EmailVerificationRepository.class);
        federatedIdentityRepository = Mockito.mock(
                com.jrobertgardzinski.security.domain.repository.FederatedIdentityRepository.class);
        enrolledFactorRepository = Mockito.mock(EnrolledFactorRepository.class);
        recoveryCodeRepository = Mockito.mock(RecoveryCodeRepository.class);
        passwordlessAccountRepository = Mockito.mock(PasswordlessAccountRepository.class);
        passwordResetRepository = Mockito.mock(PasswordResetRepository.class);
        confirmEmailChange = new ConfirmEmailChange(emailChangeRepository, userRepository,
                emailVerificationRepository, federatedIdentityRepository, enrolledFactorRepository,
                recoveryCodeRepository, passwordlessAccountRepository, passwordResetRepository);
    }

    @Example
    @Label("A matching token moves the user to the new address and marks it verified")
    void matching_token_changes_the_email() {
        Mockito.when(emailChangeRepository.confirmChange(TOKEN)).thenReturn(Optional.of(new EmailChange(OLD, NEW)));

        assertEquals(new ConfirmEmailChangeResult.EmailChanged(NEW), confirmEmailChange.execute(TOKEN));
        Mockito.verify(userRepository).updateEmail(OLD, NEW);
        Mockito.verify(emailVerificationRepository).markVerified(NEW);
    }

    @Example
    @Label("Federated links die with the old address — the provider vouched for it, not the account")
    void federated_links_are_severed() {
        Mockito.when(emailChangeRepository.confirmChange(TOKEN)).thenReturn(Optional.of(new EmailChange(OLD, NEW)));

        confirmEmailChange.execute(TOKEN);

        Mockito.verify(federatedIdentityRepository).relinkAll(OLD, NEW);
    }

    @Example
    @Label("The MFA state follows the account to its new address — factors, recovery codes, the passwordless mark")
    void mfa_state_follows_the_account() {
        // Every one of these tables is keyed by the address with no foreign key to cascade. Left
        // behind, a lookup under the new address finds nothing: the second factor disappears without
        // a trace and a password alone signs in again, the printed recovery codes stop working, and a
        // federated account reads as "has a password" — which locks its owner out of deleting it.
        Mockito.when(emailChangeRepository.confirmChange(TOKEN)).thenReturn(Optional.of(new EmailChange(OLD, NEW)));

        confirmEmailChange.execute(TOKEN);

        Mockito.verify(enrolledFactorRepository).reassign(OLD, NEW);
        Mockito.verify(recoveryCodeRepository).reassign(OLD, NEW);
        Mockito.verify(passwordlessAccountRepository).reassign(OLD, NEW);
    }

    @Example
    @Label("Tokens e-mailed to the old address are dropped — the account no longer owns that mailbox")
    void tokens_for_the_old_address_are_dropped() {
        // A reset link e-mailed before the move is matched by ADDRESS. Left pending, it would set the
        // password of whoever registers the freed address next — the same takeover the deletion path
        // had, reached through an e-mail change instead.
        Mockito.when(emailChangeRepository.confirmChange(TOKEN)).thenReturn(Optional.of(new EmailChange(OLD, NEW)));

        confirmEmailChange.execute(TOKEN);

        Mockito.verify(passwordResetRepository).purge(OLD);
        Mockito.verify(emailChangeRepository).purge(OLD);
        Mockito.verify(emailVerificationRepository).purge(OLD);
    }

    @Example
    @Label("An unknown token is rejected and no email is changed")
    void unknown_token_is_rejected() {
        Mockito.when(emailChangeRepository.confirmChange(TOKEN)).thenReturn(Optional.empty());

        assertInstanceOf(ConfirmEmailChangeResult.InvalidToken.class, confirmEmailChange.execute(TOKEN));
        Mockito.verify(userRepository, Mockito.never()).updateEmail(Mockito.any(), Mockito.any());
        Mockito.verify(enrolledFactorRepository, Mockito.never()).reassign(Mockito.any(), Mockito.any());
        Mockito.verify(passwordResetRepository, Mockito.never()).purge(Mockito.any());
    }
}

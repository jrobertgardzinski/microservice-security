package com.jrobertgardzinski.security.system.account;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.repository.EmailChangeRepository;
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
    private ConfirmEmailChange confirmEmailChange;

    @BeforeTry
    void init() {
        emailChangeRepository = Mockito.mock(EmailChangeRepository.class);
        userRepository = Mockito.mock(UserRepository.class);
        confirmEmailChange = new ConfirmEmailChange(emailChangeRepository, userRepository);
    }

    @Example
    @Label("A matching token moves the user to the new address")
    void matching_token_changes_the_email() {
        Mockito.when(emailChangeRepository.confirmChange(TOKEN)).thenReturn(Optional.of(new EmailChange(OLD, NEW)));

        assertEquals(new ConfirmEmailChangeResult.EmailChanged(NEW), confirmEmailChange.execute(TOKEN));
        Mockito.verify(userRepository).updateEmail(OLD, NEW);
    }

    @Example
    @Label("An unknown token is rejected and no email is changed")
    void unknown_token_is_rejected() {
        Mockito.when(emailChangeRepository.confirmChange(TOKEN)).thenReturn(Optional.empty());

        assertInstanceOf(ConfirmEmailChangeResult.InvalidToken.class, confirmEmailChange.execute(TOKEN));
        Mockito.verify(userRepository, Mockito.never()).updateEmail(Mockito.any(), Mockito.any());
    }
}

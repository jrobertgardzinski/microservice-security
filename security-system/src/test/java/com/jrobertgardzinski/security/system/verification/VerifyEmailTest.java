package com.jrobertgardzinski.security.system.verification;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.repository.EmailVerificationRepository;
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
@Feature("Verify email")
class VerifyEmailTest {

    private static final VerificationToken TOKEN = new VerificationToken("verification-token");
    private static final Email EMAIL = Email.of("user@example.com");

    private EmailVerificationRepository repository;
    private VerifyEmail verifyEmail;

    @BeforeTry
    void init() {
        repository = Mockito.mock(EmailVerificationRepository.class);
        verifyEmail = new VerifyEmail(repository);
    }

    @Example
    @Label("A matching token verifies the address")
    void matching_token_verifies() {
        Mockito.when(repository.completeVerification(TOKEN)).thenReturn(Optional.of(EMAIL));

        VerifyEmailResult result = verifyEmail.execute(TOKEN);

        assertEquals(new VerifyEmailResult.Verified(EMAIL), result);
    }

    @Example
    @Label("An unknown token is rejected")
    void unknown_token_is_rejected() {
        Mockito.when(repository.completeVerification(TOKEN)).thenReturn(Optional.empty());

        assertInstanceOf(VerifyEmailResult.Rejected.class, verifyEmail.execute(TOKEN));
    }
}

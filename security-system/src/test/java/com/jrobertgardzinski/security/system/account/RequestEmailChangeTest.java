package com.jrobertgardzinski.security.system.account;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.email.domain.NormalizedEmail;
import com.jrobertgardzinski.security.domain.port.EmailVerificationNotifier;
import com.jrobertgardzinski.security.domain.repository.EmailChangeRepository;
import com.jrobertgardzinski.security.domain.repository.UserRepository;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import net.jqwik.api.Example;
import net.jqwik.api.Label;
import net.jqwik.api.lifecycle.BeforeTry;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@Epic("Use case")
@Feature("Change email")
class RequestEmailChangeTest {

    private static final Email CURRENT = Email.of("user@example.com");
    private static final Email NEW = Email.of("new@example.com");

    private UserRepository userRepository;
    private EmailChangeRepository emailChangeRepository;
    private EmailVerificationNotifier notifier;
    private RequestEmailChange requestEmailChange;

    @BeforeTry
    void init() {
        userRepository = Mockito.mock(UserRepository.class);
        emailChangeRepository = Mockito.mock(EmailChangeRepository.class);
        notifier = Mockito.mock(EmailVerificationNotifier.class);
        requestEmailChange = new RequestEmailChange(userRepository, emailChangeRepository, notifier);
    }

    @Example
    @Label("A free new address starts the change and e-mails a link")
    void free_address_starts_the_change() {
        Mockito.when(userRepository.existsBy(NormalizedEmail.of(NEW))).thenReturn(false);

        assertInstanceOf(RequestEmailChangeResult.Requested.class, requestEmailChange.execute(CURRENT, NEW));
        Mockito.verify(emailChangeRepository).startChange(Mockito.any(), Mockito.any());
        Mockito.verify(notifier).sendVerificationLink(Mockito.eq(NEW), Mockito.any());
    }

    @Example
    @Label("A taken new address is refused, nothing e-mailed")
    void taken_address_is_refused() {
        Mockito.when(userRepository.existsBy(NormalizedEmail.of(NEW))).thenReturn(true);

        assertInstanceOf(RequestEmailChangeResult.EmailTaken.class, requestEmailChange.execute(CURRENT, NEW));
        Mockito.verify(emailChangeRepository, Mockito.never()).startChange(Mockito.any(), Mockito.any());
        Mockito.verify(notifier, Mockito.never()).sendVerificationLink(Mockito.any(), Mockito.any());
    }
}

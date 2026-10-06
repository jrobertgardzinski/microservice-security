package com.jrobertgardzinski.security.application.account;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.application.TransactionBoundary;
import com.jrobertgardzinski.security.domain.core.RegistrationNoticeNotifier;
import com.jrobertgardzinski.security.domain.core.IpAddress;
import com.jrobertgardzinski.security.system.account.ChangePassword;
import com.jrobertgardzinski.security.system.mailbox.ConfirmEmailChange;
import com.jrobertgardzinski.security.system.mailbox.RequestEmailChange;
import com.jrobertgardzinski.security.system.mailbox.RequestEmailChangeResult;
import com.jrobertgardzinski.security.system.core.SourceThrottle;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class AccountServiceTest {

    private static final Email CALLER = Email.of("user@example.com");

    private final ChangePassword changePassword = Mockito.mock(ChangePassword.class);
    private final RequestEmailChange requestEmailChange = Mockito.mock(RequestEmailChange.class);
    private final RegistrationNoticeNotifier notices = Mockito.mock(RegistrationNoticeNotifier.class);
    private final AccountService service = new AccountService(changePassword, requestEmailChange,
            Mockito.mock(ConfirmEmailChange.class), notices,
            new SourceThrottle(5, Duration.ofMinutes(15), Clock.systemUTC()),
            new TransactionBoundary() {
                @Override
                public <T> T execute(Supplier<T> work) {
                    return work.get();
                }
            });

    @Test
    void a_typo_in_the_new_address_does_not_spend_the_step_up() {
        AtomicBoolean spent = new AtomicBoolean();

        assertInstanceOf(AccountService.EmailChange.InvalidEmail.class,
                service.requestEmailChange(CALLER, "not-an-address", () -> {
                    spent.set(true);
                    return true;
                }));

        assertFalse(spent.get(), "the one-shot elevation was spent on an address that could never be used");
    }

    @Test
    void without_a_step_up_nothing_is_started() {
        assertInstanceOf(AccountService.EmailChange.StepUpRequired.class,
                service.requestEmailChange(CALLER, "new@example.com", () -> false));

        Mockito.verifyNoInteractions(requestEmailChange);
    }

    @Test
    void a_taken_address_looks_like_a_sent_link_and_its_owner_is_told() {
        Mockito.when(requestEmailChange.execute(Mockito.any(), Mockito.any()))
                .thenReturn(new RequestEmailChangeResult.EmailTaken());

        assertInstanceOf(AccountService.EmailChange.LinkSent.class,
                service.requestEmailChange(CALLER, "taken@example.com", () -> true));

        Mockito.verify(notices).sendAlreadyRegistered(Email.of("taken@example.com"));
    }

    @Test
    void a_blank_password_never_reaches_the_use_case() {
        assertInstanceOf(AccountService.PasswordChange.Incomplete.class,
                service.changePassword(CALLER, " ", "StrongPassword1!", new IpAddress("203.0.113.7")));

        Mockito.verifyNoInteractions(changePassword);
    }
}

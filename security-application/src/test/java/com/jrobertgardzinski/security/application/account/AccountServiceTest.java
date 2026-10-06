package com.jrobertgardzinski.security.application.account;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.application.TransactionBoundary;
import com.jrobertgardzinski.security.domain.core.IpAddress;
import com.jrobertgardzinski.security.system.account.ChangePassword;
import com.jrobertgardzinski.security.system.core.SourceThrottle;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Clock;
import java.time.Duration;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class AccountServiceTest {

    private static final Email CALLER = Email.of("user@example.com");

    private final ChangePassword changePassword = Mockito.mock(ChangePassword.class);
    private final AccountService service = new AccountService(changePassword,
            new SourceThrottle(5, Duration.ofMinutes(15), Clock.systemUTC()),
            new TransactionBoundary() {
                @Override
                public <T> T execute(Supplier<T> work) {
                    return work.get();
                }
            });

    @Test
    void a_blank_password_never_reaches_the_use_case() {
        assertInstanceOf(AccountService.PasswordChange.Incomplete.class,
                service.changePassword(CALLER, " ", "StrongPassword1!", new IpAddress("203.0.113.7")));

        Mockito.verifyNoInteractions(changePassword);
    }
}

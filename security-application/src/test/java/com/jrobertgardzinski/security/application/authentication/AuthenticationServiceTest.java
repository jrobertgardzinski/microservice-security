package com.jrobertgardzinski.security.application.authentication;

import com.jrobertgardzinski.security.application.TransactionBoundary;
import com.jrobertgardzinski.security.domain.core.IpAddress;
import com.jrobertgardzinski.security.system.authentication.Authentication;
import com.jrobertgardzinski.security.system.authentication.AuthenticationResult;
import com.jrobertgardzinski.security.system.core.SourceThrottle;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Clock;
import java.time.Duration;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class AuthenticationServiceTest {

    private static final IpAddress SOURCE = new IpAddress("203.0.113.7");

    private final Authentication authentication = Mockito.mock(Authentication.class);
    private final TransactionBoundary inline = new TransactionBoundary() {
        @Override
        public <T> T execute(Supplier<T> work) {
            return work.get();
        }
    };

    private AuthenticationService allowing(int perWindow) {
        return new AuthenticationService(authentication,
                new SourceThrottle(perWindow, Duration.ofMinutes(15), Clock.systemUTC()), inline);
    }

    @Test
    void the_use_case_answers_on_the_domain_it_was_handed() {
        Mockito.when(authentication.execute(Mockito.any())).thenReturn(new AuthenticationResult.Rejected());

        assertInstanceOf(AuthenticationService.Outcome.Rejected.class,
                allowing(5).authenticate("user@example.com", "a password", SOURCE, "curl"));
    }

    @Test
    void a_credential_the_domain_cannot_construct_never_reaches_the_use_case() {
        assertInstanceOf(AuthenticationService.Outcome.Unreadable.class,
                allowing(5).authenticate("not-an-address", "a password", SOURCE, "curl"));

        // no lookup, and therefore no brute-force count: there is no account here to guess at
        Mockito.verifyNoInteractions(authentication);
    }

    @Test
    void a_source_over_its_budget_is_throttled_before_any_lookup() {
        AuthenticationService service = allowing(1);
        Mockito.when(authentication.execute(Mockito.any())).thenReturn(new AuthenticationResult.Rejected());
        service.authenticate("user@example.com", "a password", SOURCE, "curl");

        assertInstanceOf(AuthenticationService.Outcome.Throttled.class,
                service.authenticate("user@example.com", "a password", SOURCE, "curl"));
        Mockito.verify(authentication, Mockito.times(1)).execute(Mockito.any());
    }
}

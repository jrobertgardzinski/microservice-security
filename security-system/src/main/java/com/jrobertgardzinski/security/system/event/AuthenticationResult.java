package com.jrobertgardzinski.security.system.event;

import com.jrobertgardzinski.security.domain.entity.SessionTokens;
import com.jrobertgardzinski.security.domain.event.authentication.AuthenticationEvent;
import com.jrobertgardzinski.security.domain.event.authentication.AuthenticationFailedEvent;
import com.jrobertgardzinski.security.domain.event.authentication.AuthenticationPassedEvent;
import com.jrobertgardzinski.security.domain.event.brute.force.protection.Blocked;
import com.jrobertgardzinski.security.domain.event.brute.force.protection.BruteForceProtectionEvent;
import com.jrobertgardzinski.security.domain.event.brute.force.protection.Passed;

import java.util.function.Function;
import java.util.function.Supplier;

public sealed interface AuthenticationResult permits AuthenticationBlocked, AuthenticationFailed, AuthenticationPassed {

    static AuthenticationResult from(
            BruteForceProtectionEvent bruteForce,
            Supplier<AuthenticationEvent> verifyCredentials,
            Function<AuthenticationPassedEvent, SessionTokens> onCredentialsPassed,
            Runnable onCredentialsFailed) {

        return switch (bruteForce) {
            case Blocked blocked -> new AuthenticationBlocked(blocked.authenticationBlock());
            case Passed _ -> switch (verifyCredentials.get()) {
                case AuthenticationPassedEvent passed -> new AuthenticationPassed(onCredentialsPassed.apply(passed));
                case AuthenticationFailedEvent _ -> {
                    onCredentialsFailed.run();
                    yield new AuthenticationFailed();
                }
            };
        };
    }
}

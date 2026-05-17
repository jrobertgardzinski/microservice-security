package com.jrobertgardzinski.security.domain.event.refresh;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.entity.SessionTokens;
import com.jrobertgardzinski.security.domain.vo.token.expiration.RefreshTokenExpiration;

import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

public sealed interface RefreshTokenEvent permits NoRefreshTokenFoundEvent, RefreshTokenExpiredEvent, RefreshTokenPassedEvent {

    static RefreshTokenEvent from(
            Email email,
            Optional<RefreshTokenExpiration> maybeExpiration,
            Runnable onTokenFound,
            Predicate<RefreshTokenExpiration> isExpired,
            Supplier<SessionTokens> generateFresh) {

        return maybeExpiration
                .<RefreshTokenEvent>map(expiration -> {
                    onTokenFound.run();
                    return isExpired.test(expiration)
                            ? new RefreshTokenExpiredEvent(email)
                            : new RefreshTokenPassedEvent(generateFresh.get());
                })
                .orElseGet(() -> new NoRefreshTokenFoundEvent(email));
    }
}

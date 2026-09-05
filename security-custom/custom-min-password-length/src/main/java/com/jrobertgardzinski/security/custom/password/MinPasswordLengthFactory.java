package com.jrobertgardzinski.security.custom.password;

import com.jrobertgardzinski.config.ladder.ConfigLadder;
import com.jrobertgardzinski.config.source.live.CachingLiveConfigPort;
import com.jrobertgardzinski.config.source.live.LiveConfigPort;
import com.jrobertgardzinski.config.source.restart.RestartConfigPort;
import com.jrobertgardzinski.password.policy.PasswordPolicyInForce;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;

import java.time.Clock;
import java.time.Duration;

/**
 * Wires this order: the ladder over a {@code security_settings} row and the deployment's property,
 * the admin's use case that writes the row, and the policy in force that the neutral service's
 * use cases ask. {@code @Primary} so it stands in for the neutral, restart-bound policy the
 * application declares as {@code @Secondary}; {@code @Context} so an illegal property fails the
 * boot and never the first request. The row is read through a TTL cache; the TTL is a property of
 * this deployment and a zero switches the cache off.
 */
@Factory
final class MinPasswordLengthFactory {

    @Context
    ConfigLadder<Integer> minPasswordLength(LiveConfigPort<Integer> settingsRows,
                                            RestartConfigPort<Integer> properties,
                                            Clock clock,
                                            @Value("${security.settings.cache.ttl.seconds:10}") int cacheTtlSeconds) {
        return MinPasswordLengthLadder.over(
                new CachingLiveConfigPort<>(settingsRows, Duration.ofSeconds(cacheTtlSeconds), clock),
                properties);
    }

    @Singleton
    SetMinPasswordLength setMinPasswordLength(MinLengthRepository store) {
        return new SetMinPasswordLength(store);
    }

    @Singleton
    @Primary
    PasswordPolicyInForce passwordPolicyInForce(ConfigLadder<Integer> minPasswordLength) {
        return new LadderedPasswordPolicy(minPasswordLength);
    }
}

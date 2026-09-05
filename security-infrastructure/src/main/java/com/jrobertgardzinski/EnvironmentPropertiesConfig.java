package com.jrobertgardzinski;

import com.jrobertgardzinski.config.source.restart.RestartConfigPort;
import io.micronaut.context.env.Environment;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The restart level of the configuration ladder over Micronaut's {@link Environment} —
 * application.yml, environment variables, whatever the deployment contributed. Absence and an
 * unconvertible value both report as absent (a vacant level the ladder falls through), the latter
 * with a log line, so a typo in a property can never take a use case down. The one bean of its
 * kind: the neutral policy and every custom order read the deployment through it.
 */
@Singleton
final class EnvironmentPropertiesConfig implements RestartConfigPort<Integer> {

    private static final Logger LOG = LoggerFactory.getLogger(EnvironmentPropertiesConfig.class);

    private final Environment environment;

    EnvironmentPropertiesConfig(Environment environment) {
        this.environment = environment;
    }

    @Override
    public Integer find(String name) {
        try {
            return environment.getProperty(name, Integer.class).orElse(null);
        } catch (RuntimeException unconvertible) {
            LOG.warn("property '{}' is not convertible to an integer - treating the level as vacant", name);
            return null;
        }
    }
}

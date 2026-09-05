package com.jrobertgardzinski;

import com.jrobertgardzinski.config.source.restart.RestartConfigPort;
import io.micronaut.context.env.Environment;
import jakarta.inject.Singleton;

/**
 * The restart level of the configuration ladder over Micronaut's {@link Environment} —
 * application.yml, environment variables, whatever the deployment contributed — as TEXT. What the
 * text means (an integer, a flag, a set of characters) is the application layer's translation, not
 * the adapter's: a value that is not its type is refused there, at startup, by name. Absence is a
 * vacant level the ladder falls through. The one bean of its kind: the neutral policy and every
 * custom order read the deployment through it.
 */
@Singleton
final class EnvironmentPropertiesConfig implements RestartConfigPort<String> {

    private final Environment environment;

    EnvironmentPropertiesConfig(Environment environment) {
        this.environment = environment;
    }

    @Override
    public String find(String name) {
        return environment.getProperty(name, String.class).orElse(null);
    }
}

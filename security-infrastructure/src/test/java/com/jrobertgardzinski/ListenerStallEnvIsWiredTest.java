package com.jrobertgardzinski;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The lamp's refusal message must name a knob that EXISTS.
 *
 * <p>{@link OffboardingListenerHealth} refuses to start when the configured stall tolerance sits
 * below its floor and tells the operator to raise {@link OffboardingListenerHealth#STALL_ENV} —
 * but the value is read from the property {@code security.saga.listener-stall-seconds}, and until
 * P18 poz. 41 nothing mapped that variable onto it. The operator who did as they were told changed
 * nothing and met the same refusal; the instruction pointed into a dead end.
 *
 * <p>This asserts the wiring in the DEPLOYED file, against the CONSTANT the message is built from,
 * so the two cannot drift apart again: rename the constant and this test fails, drop the line from
 * {@code application.yml} and it fails too. It reads the file from disk on purpose — the point is
 * what ships, not what a test context happens to be handed (the lesson the twins' probe-url and
 * offset-reset pins already carry).
 */
class ListenerStallEnvIsWiredTest {

    private static final Path DEPLOYED = Path.of("src/main/resources/application.yml");

    @Test
    void the_variable_the_refusal_message_names_is_mapped_onto_the_property_it_reads()
            throws IOException {
        String shipped = Files.readString(DEPLOYED);

        assertTrue(shipped.contains("listener-stall-seconds:"),
                "application.yml must map the stall tolerance, or the property has no operator knob"
                        + " at all — it silently keeps the @Value default");
        assertTrue(shipped.contains("${" + OffboardingListenerHealth.STALL_ENV + ":"),
                "the refusal message names " + OffboardingListenerHealth.STALL_ENV
                        + ", so application.yml must read THAT variable — otherwise the instruction"
                        + " an operator follows changes nothing");
    }
}

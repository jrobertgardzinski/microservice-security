package com.jrobertgardzinski;

import io.micronaut.configuration.kafka.ConsumerRegistry;
import io.micronaut.health.HealthStatus;
import io.micronaut.management.health.indicator.HealthResult;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The lamp identity did not have, and the reason it now needs one:
 * {@link OffboardingOutcomeListener} gives up on a partition when its retries are spent —
 * {@code stopOnExhaustedRetry} pauses it for good rather than stopping the container, which is a
 * listener neither dead nor working, and every other participant of the saga could already report
 * exactly that.
 *
 * <p>Each case below is a real situation. A loop with nothing to read (the normal state of
 * {@code offboarding-events}, which is silent for days between deletions) must read as ALIVE — get
 * that wrong and readiness takes identity out of the Service for both products, every day. A loop
 * that has stopped must read as DOWN even though the topic looks exactly as quiet.
 */
class OffboardingListenerHealthTest {

    private static final String CONSUMER = "security-offboarding-outcome-0";
    private static final Duration TOLERANCE = Duration.ofSeconds(120);

    private final ConsumerRegistry registry = mock(ConsumerRegistry.class);
    private long nowNanos;

    private OffboardingListenerHealth lamp() {
        return new OffboardingListenerHealth(registry, TOLERANCE.toSeconds(), () -> nowNanos);
    }

    private void elapse(Duration time) {
        nowNanos += time.toNanos();
    }

    /** A registered consumer on the outcome topic whose last poll was this long ago. */
    private void aConsumerLastPolling(Duration ago) {
        Consumer<?, ?> consumer = mock(Consumer.class);
        MetricName name = new MetricName("last-poll-seconds-ago", "consumer-metrics", "", Map.of());
        Metric metric = new Metric() {
            @Override
            public MetricName metricName() {
                return name;
            }

            @Override
            public Object metricValue() {
                return (double) ago.toSeconds();
            }
        };
        when(consumer.metrics()).thenAnswer(call -> Map.of(name, metric));
        when(registry.getConsumerIds()).thenReturn(Set.of(CONSUMER));
        when(registry.getConsumerSubscription(CONSUMER))
                .thenReturn(Set.of(OffboardingListenerHealth.TOPIC));
        when(registry.getConsumer(anyString())).thenAnswer(call -> consumer);
    }

    /**
     * Reads the one result the lamp publishes, by subscribing — no reactor.
     *
     * <p>Flux.from(...).blockFirst() is the obvious way to write this and it cost a broken service:
     * reactor-core reaches this module transitively at RUNTIME scope for the Netty server, and
     * declaring it `test` here to get Flux narrowed that scope, dropped it out of target/lib and
     * killed the packaged app on startup — with a green `mvn verify`, because tests have their own
     * classpath. The Publisher is synchronous, so this is both smaller and closer to the contract.
     */
    private HealthResult health(OffboardingListenerHealth lamp) {
        java.util.concurrent.atomic.AtomicReference<HealthResult> reported =
                new java.util.concurrent.atomic.AtomicReference<>();
        lamp.getResult().subscribe(new Subscriber<>() {
            @Override
            public void onSubscribe(Subscription subscription) {
                subscription.request(1);
            }

            @Override
            public void onNext(HealthResult result) {
                reported.set(result);
            }

            @Override
            public void onError(Throwable failure) {
                throw new AssertionError("the lamp must not fail its subscriber", failure);
            }

            @Override
            public void onComplete() {
            }
        });
        return java.util.Objects.requireNonNull(reported.get(), "the lamp published nothing");
    }

    @Test
    @DisplayName("a loop with nothing to read is UP — this topic is silent for days on purpose")
    void an_idle_loop_is_up() {
        aConsumerLastPolling(Duration.ofMillis(200));
        // the lamp is built FIRST and aged afterwards: its boot grace caps the reported age, so a
        // lamp created after the clock moved would have a grace of zero and call everything healthy
        OffboardingListenerHealth lamp = lamp();
        elapse(Duration.ofHours(6));

        HealthResult reported = health(lamp);

        assertEquals(HealthStatus.UP, reported.getStatus(),
                "a consumer polls whether or not there is anything to consume; counting records"
                        + " here would red-light identity on every quiet day");
        assertTrue(String.valueOf(details(reported).get(CONSUMER)).contains("polling"),
                String.valueOf(reported.getDetails()));
    }

    @Test
    @DisplayName("a loop that stopped polling is DOWN, and the detail says how long ago")
    void a_stopped_loop_is_down() {
        aConsumerLastPolling(TOLERANCE.plusSeconds(30));
        OffboardingListenerHealth lamp = lamp();
        elapse(Duration.ofHours(6));

        HealthResult reported = health(lamp);

        assertEquals(HealthStatus.DOWN, reported.getStatus(),
                "a loop that stopped polling at all — a dead consumer thread, a wedged handler."
                        + " The OTHER standstill this service can produce, a partition given up on"
                        + " after the retries are spent, leaves the poll counter healthy and is"
                        + " caught by the paused-partition branch instead");
        assertTrue(String.valueOf(details(reported).get(CONSUMER)).contains("no poll for"),
                String.valueOf(reported.getDetails()));
    }

    /** The partition this consumer is assigned, and whether the framework reports it paused. */
    private void assignedPartition(boolean paused) {
        org.apache.kafka.common.TopicPartition partition =
                new org.apache.kafka.common.TopicPartition(OffboardingListenerHealth.TOPIC, 0);
        when(registry.getConsumerAssignment(CONSUMER)).thenReturn(Set.of(partition));
        when(registry.isPaused(CONSUMER, Set.of(partition))).thenReturn(paused);
    }

    @Test
    @DisplayName("a partition paused past the whole retry budget is DOWN, even though the loop still polls")
    void an_abandoned_partition_is_down() {
        // THE case this lamp exists for, and the one it could not see until this branch existed.
        // stopOnExhaustedRetry does not stop the container: it seeks, logs once and pauses that one
        // partition, which resumeTopicPartitions then refuses to resume. The loop keeps turning on
        // whatever else it holds, so the poll counter stays healthy — 0s ago, for ever — while the
        // message that finishes an account deletion sits unread and the portal has already erased
        // the user's content.
        aConsumerLastPolling(Duration.ofMillis(200));
        assignedPartition(true);
        OffboardingListenerHealth lamp = lamp();
        // the verdict needs two sightings: the first starts the pause clock, and only a pause that
        // outlives the retry budget — no scheduled resume left to lift it — is "given up on"
        assertEquals(HealthStatus.UP, health(lamp).getStatus());
        elapse(OffboardingListenerHealth.ABANDON_TOLERANCE.plusSeconds(1));

        HealthResult reported = health(lamp);

        assertEquals(HealthStatus.DOWN, reported.getStatus(),
                "the poll counter says this consumer is perfectly alive, and it is — for every"
                        + " partition except the one carrying the outcome nobody will now read");
        assertTrue(String.valueOf(details(reported).get(CONSUMER)).contains("gave up on"),
                String.valueOf(reported.getDetails()));
        assertTrue(String.valueOf(details(reported).get(CONSUMER)).contains("restart"),
                "and the detail says what an operator is supposed to DO about it: "
                        + reported.getDetails());
    }

    @Test
    @DisplayName("a partition merely backing off between retries is not 'given up on'")
    void a_partition_between_retries_is_up() {
        // The discriminator is DURATION, not the pause itself: in micronaut-kafka 6.1.0 a retry's
        // backoff goes through the same ConsumerState#pause as stopOnExhaustedRetry, so isPaused
        // answers true through every backoff window — up to 512s straight for this listener's
        // budget. A lamp that believed the flag alone went red on a plain database hiccup,
        // readiness pulled identity from the Service for both products during the healthiest
        // possible recovery, and the message told the operator to restart a service about to fix
        // itself.
        aConsumerLastPolling(Duration.ofMillis(200));
        assignedPartition(true);
        OffboardingListenerHealth lamp = lamp();
        assertEquals(HealthStatus.UP, health(lamp).getStatus());
        elapse(Duration.ofSeconds(512));

        assertEquals(HealthStatus.UP, health(lamp).getStatus(),
                "a pause still inside the retry budget has a resume scheduled — this recovers on"
                        + " its own, and DOWN here takes sign-in away for nothing");
    }

    @Test
    @DisplayName("a pause that resumed forgets its history — two incidents days apart do not add up")
    void a_resumed_pause_starts_a_fresh_clock() {
        // Without the reset, a backoff on Monday and another on Thursday would sum past the
        // tolerance and report a partition as abandoned while its second retry budget is running.
        aConsumerLastPolling(Duration.ofMillis(200));
        assignedPartition(true);
        OffboardingListenerHealth lamp = lamp();
        assertEquals(HealthStatus.UP, health(lamp).getStatus());
        elapse(Duration.ofMinutes(15));

        assignedPartition(false);
        assertEquals(HealthStatus.UP, health(lamp).getStatus());

        assignedPartition(true);
        assertEquals(HealthStatus.UP, health(lamp).getStatus());
        elapse(Duration.ofMinutes(15));

        assertEquals(HealthStatus.UP, health(lamp).getStatus(),
                "fifteen paused minutes, a resume, and fifteen more are two healthy backoffs,"
                        + " not thirty minutes of abandonment");
    }

    @Test
    @DisplayName("no consumer on the topic at all is DOWN — the loudest version of the same failure")
    void a_missing_listener_is_down() {
        when(registry.getConsumerIds()).thenReturn(Set.of("something-else"));
        when(registry.getConsumerSubscription("something-else")).thenReturn(Set.of("other-topic"));

        assertEquals(HealthStatus.DOWN, health(lamp()).getStatus(),
                "a listener that never came up breaks every account deletion just as thoroughly"
                        + " as one that stopped later");
    }

    @Test
    @DisplayName("a service still booting is not born unhealthy")
    void a_warming_up_service_is_up() {
        // Kafka's counter measures from the last poll, and before the FIRST poll there is none:
        // the value is the age of the epoch. Reported literally, every boot would begin with a
        // fifty-six-year stall.
        aConsumerLastPolling(Duration.ofDays(20_000));
        OffboardingListenerHealth lamp = lamp();
        elapse(Duration.ofSeconds(2));

        assertEquals(HealthStatus.UP, health(lamp).getStatus(),
                "the grace runs from this lamp's creation, and a live consumer polls within"
                        + " milliseconds of starting");
    }

    @Test
    @DisplayName("but a consumer that never polls goes DOWN once the tolerance is spent")
    void a_consumer_that_never_polls_goes_down() {
        aConsumerLastPolling(Duration.ofDays(20_000));
        OffboardingListenerHealth lamp = lamp();
        elapse(TOLERANCE.plusSeconds(1));

        assertEquals(HealthStatus.DOWN, health(lamp).getStatus());
    }

    /**
     * The container has to be able to BUILD this thing, and no test that calls a constructor
     * directly can tell you whether it can.
     *
     * <p>This class has two constructors — the injected one and the one these tests use to steer
     * the clock. Micronaut picks the injectable constructor by annotation, and with neither
     * annotated it falls back to looking for a no-arg one; not finding it, it fails at RUNTIME
     * with {@code method 'void <init>()' not found}. That is not a small failure: it is thrown
     * while building {@code HealthEndpoint}, so /health, /health/readiness and /health/liveness all
     * answer 500 and the container never reports healthy — while `mvn verify` stays entirely
     * green, because every test here constructs the bean by hand.
     *
     * <p>The CI boot smoke DOES cover it, since {@code 3b837e1} moved that step to the {@code dev}
     * environment with a real database and the Kafka layer, and made it assert that
     * {@code offboardingListener} appears in {@code /health} — dropping the {@code @Inject} turns it
     * red with "/health answered 500". This paragraph said the opposite for three hours, which is
     * the same defect in a coverage map that P16 poz. 11 found: two documents in one repository
     * disagreeing about what a gate checks. This test stays because it is the faster and smaller of
     * the two guards — it fails without Docker, without Postgres and without booting anything.
     */
    @Test
    @DisplayName("the container knows which constructor to use")
    void the_container_knows_which_constructor_to_use() {
        java.lang.reflect.Constructor<?>[] constructors =
                OffboardingListenerHealth.class.getDeclaredConstructors();

        long injectable = java.util.Arrays.stream(constructors)
                .filter(constructor -> constructor.isAnnotationPresent(jakarta.inject.Inject.class))
                .count();

        assertEquals(constructors.length > 1 ? 1 : 0, injectable,
                "with " + constructors.length + " constructors and " + injectable + " marked"
                        + " @Inject, Micronaut cannot choose: it looks for a no-arg constructor,"
                        + " fails to find one, and every health endpoint answers 500 at runtime"
                        + " while this suite stays green");
    }

    @Test
    @DisplayName("a tolerance below one handler invocation is refused, by name")
    void a_tolerance_below_the_floor_is_refused() {
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> new OffboardingListenerHealth(registry, 5, () -> nowNanos));

        assertTrue(refused.getMessage().contains(OffboardingListenerHealth.STALL_ENV),
                "the message must name the variable and the fix: " + refused.getMessage());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> details(HealthResult reported) {
        return (Map<String, Object>) reported.getDetails();
    }
}

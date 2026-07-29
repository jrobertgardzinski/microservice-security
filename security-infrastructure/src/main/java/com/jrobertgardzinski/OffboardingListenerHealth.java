package com.jrobertgardzinski;

import io.micronaut.configuration.kafka.ConsumerRegistry;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.health.HealthStatus;
import io.micronaut.management.health.indicator.HealthIndicator;
import io.micronaut.management.health.indicator.HealthResult;
import io.micronaut.management.health.indicator.annotation.Readiness;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;
import org.reactivestreams.Publisher;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.LongSupplier;

/**
 * The lamp for this service's Kafka listener loop — the last participant of the account-deletion
 * saga that did not have one.
 *
 * <p>memes and comments have {@code SagaListenersHealth}, user-collections has
 * {@code PurgeCommandsConsumer.healthy()}, offboarding has {@code KafkaLoop.healthy()}. Identity
 * had nothing, and on 2026-07-29 that stopped being merely inconsistent:
 * {@link OffboardingOutcomeListener} was given {@code stopOnExhaustedRetry}, so a run of failures
 * no longer skips the record — it PAUSES that partition for good (see {@link #abandonedPartitions}
 * for what the setting really does; the first version of this class, and the listener's own
 * javadoc, both said "stops the container", which is not the mechanism). That is the right trade —
 * an unread outcome a restart will replay beats one silently discarded — but it exchanges a silent
 * data loss for a silent standstill, and a standstill nobody can see is only better in principle.
 *
 * <p><strong>What a stopped loop costs here.</strong> {@code offboarding-events} carries the ONE
 * message that finishes an account deletion. With the loop stopped, the portal has already erased
 * every meme, comment and collection, the saga sits STARTED, and after the sweeper's window the
 * user gets an apology mail and an account with nothing in it. HTTP keeps working throughout:
 * sign-in, refresh, everything. Nothing else in this service would go red.
 *
 * <p><strong>Readiness, not liveness</strong> — the split the rest of the estate arrived at. A
 * stopped listener is not a dead process, and a restart cannot fix the broker or the database that
 * stopped it; wiring this to liveness would crash-loop the identity service, which every product
 * shares, through any dependency outage.
 *
 * <h2>How it knows</h2>
 * Two questions, because this service can stand still in two different ways and neither signal sees
 * the other's failure:
 * <ul>
 *   <li><b>has it given up on a partition?</b> — {@code isPaused}, see {@link #abandonedPartitions}.
 *       This is the state {@code stopOnExhaustedRetry} actually produces, and the poll counter is
 *       blind to it: the loop carries on polling the partitions it did not abandon;</li>
 *   <li><b>is the loop turning at all?</b> — {@code last-poll-seconds-ago}, below. This catches a
 *       dead consumer thread or a handler wedged inside one record, which leaves no partition
 *       paused.</li>
 * </ul>
 * The first version of this lamp asked only the second question, on the strength of a javadoc that
 * described a mechanism the framework does not have — so it reported "polling, last poll 0s ago"
 * through the exact failure it was built for.
 *
 * <p>On the poll counter specifically: two other candidates were tried first and are wrong:
 * <ul>
 *   <li>{@code ConsumerRegistry.getConsumerIds()} never drops a stopped consumer (nothing removes
 *       it from the processor's map), so "the id is registered" proves the bean exists and nothing
 *       about the loop — the exact shape of signal this estate keeps having to replace;</li>
 *   <li>counting records is useless on a topic that is silent for days between deletions, which is
 *       why the two Spring participants had to grow an idle-event heartbeat instead.</li>
 * </ul>
 * The poll counter has neither problem: a consumer with nothing to read still polls, and a
 * consumer that has stopped does not. Reading it is safe from this thread — {@code metrics()} is
 * the one {@code KafkaConsumer} method that takes no lock, and it is what Micrometer scrapes.
 *
 * <p>This is deliberately NOT a broker probe. With Kafka unreachable the loop keeps polling and
 * this lamp stays green; a broker outage is reported by the participants whose loops probe it, and
 * red-lighting identity because Kafka blinked would take sign-in away from both products for a
 * fault that has nothing to do with signing in.
 */
@Singleton
@Readiness
@Requires(notEnv = "test")
@Requires(beans = ConsumerRegistry.class)
class OffboardingListenerHealth implements HealthIndicator {

    /** The topic {@link OffboardingOutcomeListener} subscribes to — the lamp finds it by that. */
    static final String TOPIC = "offboarding-events";

    static final String NAME = "offboardingListener";

    /** The operator's spelling of the tolerance, for the message they read when it is refused. */
    static final String STALL_ENV = "SECURITY_LISTENER_STALL_SEC";

    /**
     * The floor, and it is arithmetic rather than taste.
     *
     * <p>The only thing that legitimately keeps this loop from polling is ONE handler invocation:
     * a transaction that may wait out the datasource's connection timeout before it even begins.
     * The retries do NOT hold the thread — micronaut-kafka pauses the partition and keeps polling,
     * resuming when the delay is up (there is no sleep on the poll thread anywhere in
     * {@code ConsumerState}), which is why this floor is a fraction of the 150s the comments
     * participant needs, where the retries sleep in the listener.
     */
    static final Duration STALL_FLOOR = Duration.ofSeconds(60);

    private final ConsumerRegistry consumers;
    private final Duration stallTolerance;

    /**
     * Elapsed time only, from a monotonic source: a wall-clock step backwards would fake a stall
     * and a step forwards would hide one. Injected so a test can age the lamp instead of waiting.
     */
    private final LongSupplier nanoTime;

    /**
     * When this lamp came into being — the grace for a consumer that has not polled YET.
     *
     * <p>Kafka's counter is "now minus the last poll", and before the first poll there is no last
     * poll: the value is the time since the epoch. Without this, every boot would report a stall of
     * some fifty-six years for the second or two before the consumer gets going.
     */
    private final long startedNanos;

    /**
     * The one the container uses, and it says so.
     *
     * <p>{@code @Inject} is not decoration here: with two constructors and neither annotated,
     * Micronaut looks for a no-arg one, does not find it, and the failure arrives at RUNTIME as
     * "method 'void &lt;init&gt;()' not found" — while every unit test, which calls a constructor
     * directly, passes. The whole health endpoint then 500s, which means all three probes fail and
     * the container never reports healthy. Pinned by
     * {@code OffboardingListenerHealthTest#the_container_knows_which_constructor_to_use}.
     */
    @Inject
    OffboardingListenerHealth(ConsumerRegistry consumers,
                              @Value("${security.saga.listener-stall-seconds:120}") long stallSeconds) {
        this(consumers, stallSeconds, System::nanoTime);
    }

    OffboardingListenerHealth(ConsumerRegistry consumers, long stallSeconds, LongSupplier nanoTime) {
        this.consumers = consumers;
        this.stallTolerance = flooredStall(stallSeconds);
        this.nanoTime = nanoTime;
        this.startedNanos = nanoTime.getAsLong();
    }

    /**
     * Configuration validated with a floor and a message naming the variable and the fix — the
     * house pattern, because a tolerance below one handler invocation is not a preference, it is a
     * lamp that cries wolf until an operator learns to ignore it.
     */
    static Duration flooredStall(long configuredSeconds) {
        Duration configured = Duration.ofSeconds(configuredSeconds);
        if (configured.compareTo(STALL_FLOOR) >= 0) {
            return configured;
        }
        throw new IllegalArgumentException(STALL_ENV + "=" + configuredSeconds + " is below the "
                + STALL_FLOOR.toSeconds() + "s floor: one outcome may legitimately hold the poll"
                + " thread for a whole transaction, including the wait for a database connection,"
                + " so a smaller tolerance would report a working listener as a stopped one."
                + " Raise it or leave it unset.");
    }

    @Override
    public Publisher<HealthResult> getResult() {
        Set<String> listening = consumersOn(TOPIC);
        if (listening.isEmpty()) {
            return Publishers.just(HealthResult.builder(NAME, HealthStatus.DOWN)
                    .details(Map.of("listener", "no consumer is subscribed to " + TOPIC
                            + " — every account deletion stops closing"))
                    .build());
        }
        Map<String, Object> states = new LinkedHashMap<>();
        boolean stalled = false;
        for (String id : listening) {
            Set<org.apache.kafka.common.TopicPartition> abandoned = abandonedPartitions(id);
            if (!abandoned.isEmpty()) {
                stalled = true;
                states.put(id, "gave up on " + abandoned + " after the retries were spent — those"
                        + " partitions are paused for good, the outcome of a deletion sits there"
                        + " unread, and only a restart replays it");
                continue;
            }
            Optional<Duration> sinceLastPoll = sinceLastPoll(id);
            if (sinceLastPoll.isEmpty()) {
                stalled = true;
                states.put(id, "the consumer is gone — its poll counter cannot be read");
                continue;
            }
            Duration age = sinceLastPoll.get();
            if (age.compareTo(stallTolerance) > 0) {
                stalled = true;
                states.put(id, "no poll for " + age.toSeconds() + "s (tolerance "
                        + stallTolerance.toSeconds() + "s) — the outcome of a deletion is not"
                        + " being read");
            } else {
                states.put(id, "polling, last poll " + age.toSeconds() + "s ago");
            }
        }
        return Publishers.just(HealthResult.builder(NAME, stalled ? HealthStatus.DOWN : HealthStatus.UP)
                .details(states)
                .build());
    }

    /**
     * The partitions this consumer has GIVEN UP on — the state the lamp was built for, and the one
     * it could not see until 2026-07-29 evening.
     *
     * <p>The first version watched only the poll counter, on the strength of a javadoc claiming
     * that {@code stopOnExhaustedRetry} "STOPS the container". It does not. In micronaut-kafka
     * 6.1.0 it does exactly three things — {@code seek} back to the failed offset,
     * {@code handleException} once, and {@code pause} that one partition — and then the poll loop
     * carries on for ever. {@code last-poll-seconds-ago} keeps resetting, so the lamp reported
     * "polling, last poll 0s ago" while the record that finishes an account deletion sat unread.
     * A signal that cannot see the failure it was written for is worse than none, because it is
     * believed.
     *
     * <p>{@code isPaused} is the clean discriminator, and that is a property of the framework
     * rather than a guess: a retry's backoff pause is applied to the Kafka consumer directly, while
     * {@code stopOnExhaustedRetry} goes through {@code ConsumerState#pause}, which records the
     * partition in {@code pauseRequests} — and {@code resumeTopicPartitions} filters exactly those
     * out, so they are never resumed. {@code ConsumerRegistry#isPaused} requires the partition to
     * be in BOTH sets, so it answers true for "given up on" and false for "backing off between
     * attempts". No timer, no tolerance, no window in which the answer is ambiguous.
     */
    private Set<org.apache.kafka.common.TopicPartition> abandonedPartitions(String id) {
        try {
            return consumers.getConsumerAssignment(id).stream()
                    .filter(partition -> consumers.isPaused(id, Set.of(partition)))
                    .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        } catch (RuntimeException gone) {
            // the consumer was closed between the id listing and this call; the poll-counter branch
            // below reports that case, and reporting it twice would only muddy the details
            return Set.of();
        }
    }

    /** Every registered consumer whose subscription includes this topic. */
    private Set<String> consumersOn(String topic) {
        return consumers.getConsumerIds().stream()
                .filter(id -> subscriptionOf(id).contains(topic))
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    private Set<String> subscriptionOf(String id) {
        try {
            return consumers.getConsumerSubscription(id);
        } catch (RuntimeException gone) {
            return Set.of();
        }
    }

    /**
     * How long ago this consumer polled, capped by how long this lamp has existed.
     *
     * <p>The cap is the boot grace: before the first poll the counter measures from the epoch, and
     * a service must not be born unhealthy. After the first poll the cap stops mattering, because
     * the real age is the smaller of the two.
     */
    private Optional<Duration> sinceLastPoll(String id) {
        Duration sinceThisLampExisted = Duration.ofNanos(Math.max(0, nanoTime.getAsLong() - startedNanos));
        return lastPollSecondsAgo(id).map(seconds -> {
            Duration reported = Duration.ofSeconds((long) Math.max(0, seconds));
            return reported.compareTo(sinceThisLampExisted) < 0 ? reported : sinceThisLampExisted;
        });
    }

    private Optional<Double> lastPollSecondsAgo(String id) {
        try {
            Map<MetricName, ? extends Metric> metrics = consumers.getConsumer(id).metrics();
            return metrics.entrySet().stream()
                    .filter(metric -> "consumer-metrics".equals(metric.getKey().group())
                            && "last-poll-seconds-ago".equals(metric.getKey().name()))
                    .map(metric -> metric.getValue().metricValue())
                    .filter(Number.class::isInstance)
                    .map(value -> ((Number) value).doubleValue())
                    .findFirst();
        } catch (RuntimeException gone) {
            // the consumer was closed between the id listing and this call
            return Optional.empty();
        }
    }
}

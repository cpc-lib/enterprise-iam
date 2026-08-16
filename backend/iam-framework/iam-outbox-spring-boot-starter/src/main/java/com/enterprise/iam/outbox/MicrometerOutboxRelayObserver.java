package com.enterprise.iam.outbox;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Low-cardinality metrics: outcome only; never tenant, event ID or error text. */
public final class MicrometerOutboxRelayObserver implements OutboxRelayObserver {

    private final Counter claimed;
    private final Map<OutboxRelayOutcome, Counter> completed;
    private final Map<OutboxRelayOutcome, Timer> duration;

    public MicrometerOutboxRelayObserver(MeterRegistry registry) {
        Objects.requireNonNull(registry, "registry must not be null");
        this.claimed = Counter.builder("iam.outbox.claimed")
                .description("Outbox rows claimed for delivery")
                .register(registry);
        Map<OutboxRelayOutcome, Counter> counters =
                new EnumMap<>(OutboxRelayOutcome.class);
        Map<OutboxRelayOutcome, Timer> timers =
                new EnumMap<>(OutboxRelayOutcome.class);
        for (OutboxRelayOutcome outcome : OutboxRelayOutcome.values()) {
            String result = outcome.name().toLowerCase(java.util.Locale.ROOT);
            counters.put(outcome, Counter.builder("iam.outbox.delivery")
                    .description("Outbox delivery outcomes")
                    .tag("result", result)
                    .register(registry));
            timers.put(outcome, Timer.builder("iam.outbox.delivery.duration")
                    .description("Outbox handler and state transition duration")
                    .tag("result", result)
                    .register(registry));
        }
        this.completed = Map.copyOf(counters);
        this.duration = Map.copyOf(timers);
    }

    @Override
    public void claimed(int count) {
        if (count > 0) {
            claimed.increment(count);
        }
    }

    @Override
    public void completed(OutboxRelayOutcome outcome, Duration elapsed) {
        Objects.requireNonNull(outcome, "outcome must not be null");
        Objects.requireNonNull(elapsed, "elapsed must not be null");
        completed.get(outcome).increment();
        duration.get(outcome).record(elapsed);
    }
}

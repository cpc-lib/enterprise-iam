package com.enterprise.iam.outbox;

import java.time.Duration;
import java.util.Objects;

/** Bounded exponential retry with deterministic ±20% event jitter. */
public final class OutboxRetryPolicy {

    private final long initialMillis;
    private final long maximumMillis;

    public OutboxRetryPolicy(Duration initial, Duration maximum) {
        Objects.requireNonNull(initial, "initial must not be null");
        Objects.requireNonNull(maximum, "maximum must not be null");
        if (initial.isZero() || initial.isNegative() || maximum.compareTo(initial) < 0
                || initial.toMillis() < 1) {
            throw new IllegalArgumentException("retry durations are invalid");
        }
        this.initialMillis = initial.toMillis();
        this.maximumMillis = maximum.toMillis();
    }

    public Duration delay(long eventId, int attempt) {
        OutboxEvent.requirePositive(eventId, "eventId");
        if (attempt <= 0) {
            throw new IllegalArgumentException("attempt must be positive");
        }
        long value = initialMillis;
        for (int index = 1; index < attempt && value < maximumMillis; index++) {
            value = value > maximumMillis / 2 ? maximumMillis : value * 2;
        }
        int jitterPercent = 80 + Math.floorMod(Long.hashCode(eventId), 41);
        long jittered = value > Long.MAX_VALUE / jitterPercent
                ? maximumMillis
                : value * jitterPercent / 100;
        return Duration.ofMillis(Math.min(maximumMillis, Math.max(1, jittered)));
    }
}

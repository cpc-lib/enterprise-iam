package com.enterprise.iam.outbox;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Single-poll relay. Database claims are short; handlers run outside the claim
 * transaction and must tolerate duplicate delivery after a crash or lease loss.
 */
public final class OutboxRelay {

    static final String UNSUPPORTED_EVENT_SCHEMA = "UNSUPPORTED_EVENT_SCHEMA";

    private final OutboxRepository repository;
    private final OutboxHandlerRegistry handlers;
    private final OutboxRetryPolicy retryPolicy;
    private final OutboxRelayObserver observer;
    private final OutboxRelayProperties properties;
    private final Clock clock;
    private final String claimOwner;

    public OutboxRelay(
            OutboxRepository repository,
            OutboxHandlerRegistry handlers,
            OutboxRetryPolicy retryPolicy,
            OutboxRelayObserver observer,
            OutboxRelayProperties properties,
            Clock clock,
            String claimOwner) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.handlers = Objects.requireNonNull(handlers, "handlers must not be null");
        if (handlers.isEmpty()) {
            throw new IllegalStateException("enabled outbox relay requires at least one handler");
        }
        this.retryPolicy = Objects.requireNonNull(retryPolicy, "retryPolicy must not be null");
        this.observer = Objects.requireNonNull(observer, "observer must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        properties.validateEnabled();
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.claimOwner = Objects.requireNonNull(claimOwner, "claimOwner must not be null");
    }

    public int relayOnce() {
        Instant now = clock.instant();
        List<OutboxEvent> events = repository.claimBatch(
                claimOwner,
                now,
                now.plus(properties.getLeaseDuration()),
                properties.getBatchSize());
        observer.claimed(events.size());
        for (OutboxEvent event : events) {
            deliver(event);
        }
        return events.size();
    }

    private void deliver(OutboxEvent event) {
        long started = System.nanoTime();
        var handler = handlers.find(event.eventType(), event.schemaVersion());
        if (handler.isEmpty()) {
            try {
                repository.markDead(
                        event.id(), claimOwner, nextAttempt(event), UNSUPPORTED_EVENT_SCHEMA);
                record(OutboxRelayOutcome.UNSUPPORTED, started);
            } catch (OutboxLeaseLostException exception) {
                record(OutboxRelayOutcome.LEASE_LOST, started);
            }
            return;
        }
        try {
            handler.orElseThrow().handle(event);
            repository.markPublished(event.id(), claimOwner, clock.instant());
            record(OutboxRelayOutcome.PUBLISHED, started);
        } catch (OutboxLeaseLostException exception) {
            record(OutboxRelayOutcome.LEASE_LOST, started);
        } catch (OutboxNonRetryableException exception) {
            handleNonRetryable(event, exception, started);
        } catch (RuntimeException exception) {
            handleFailure(event, exception, started);
        }
    }

    private void handleNonRetryable(
            OutboxEvent event,
            OutboxNonRetryableException failure,
            long started) {
        try {
            repository.markDead(
                    event.id(), claimOwner, nextAttempt(event), failure.failureCode());
            record(OutboxRelayOutcome.DEAD, started);
        } catch (OutboxLeaseLostException exception) {
            record(OutboxRelayOutcome.LEASE_LOST, started);
        }
    }

    private void handleFailure(OutboxEvent event, RuntimeException failure, long started) {
        int attempt = nextAttempt(event);
        try {
            if (attempt >= properties.getMaxAttempts()) {
                repository.markDead(
                        event.id(), claimOwner, attempt, failureCode(failure));
                record(OutboxRelayOutcome.DEAD, started);
            } else {
                Instant nextRetryAt = clock.instant().plus(
                        retryPolicy.delay(event.eventId(), attempt));
                repository.reschedule(
                        event.id(), claimOwner, attempt, nextRetryAt, failureCode(failure));
                record(OutboxRelayOutcome.RETRY_SCHEDULED, started);
            }
        } catch (OutboxLeaseLostException exception) {
            record(OutboxRelayOutcome.LEASE_LOST, started);
        }
    }

    private void record(OutboxRelayOutcome outcome, long started) {
        long elapsed = Math.max(0, System.nanoTime() - started);
        observer.completed(outcome, Duration.ofNanos(elapsed));
    }

    private static int nextAttempt(OutboxEvent event) {
        return Math.addExact(event.retryCount(), 1);
    }

    static String failureCode(RuntimeException failure) {
        String simpleName = failure.getClass().getSimpleName();
        String safe = simpleName.replaceAll("[^A-Za-z0-9._:-]", "_");
        if (safe.isBlank()) {
            safe = "RuntimeException";
        }
        String code = "HANDLER_" + safe;
        return code.length() <= 128 ? code : code.substring(0, 128);
    }
}

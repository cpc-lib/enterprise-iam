package com.enterprise.iam.outbox;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxRelayTest {

    private static final Instant NOW = Instant.parse("2026-08-12T12:00:00Z");

    @Test
    void publishesSuccessfulDelivery() {
        Fixture fixture = new Fixture(event("event.ok", 0), event -> { });

        assertThat(fixture.relay.relayOnce()).isEqualTo(1);
        assertThat(fixture.repository.transition).isEqualTo("PUBLISHED");
        assertThat(fixture.observer.outcomes).containsExactly(OutboxRelayOutcome.PUBLISHED);
    }

    @Test
    void retriesTransientFailureWithoutPersistingExceptionMessage() {
        Fixture fixture = new Fixture(event("event.retry", 0), event -> {
            throw new IllegalStateException("secret customer payload");
        });

        fixture.relay.relayOnce();

        assertThat(fixture.repository.transition).isEqualTo("PENDING");
        assertThat(fixture.repository.retryCount).isEqualTo(1);
        assertThat(fixture.repository.errorCode).isEqualTo("HANDLER_IllegalStateException")
                .doesNotContain("secret", "payload");
        assertThat(fixture.observer.outcomes)
                .containsExactly(OutboxRelayOutcome.RETRY_SCHEDULED);
    }

    @Test
    void deadLettersAtConfiguredMaximumAttempt() {
        Fixture fixture = new Fixture(event("event.retry", 9), event -> {
            throw new IllegalStateException("temporary until retry budget ends");
        });

        fixture.relay.relayOnce();

        assertThat(fixture.repository.transition).isEqualTo("DEAD");
        assertThat(fixture.repository.retryCount).isEqualTo(10);
        assertThat(fixture.observer.outcomes).containsExactly(OutboxRelayOutcome.DEAD);
    }

    @Test
    void deadLettersPermanentAndUnsupportedEvents() {
        Fixture permanent = new Fixture(event("event.dead", 0), event -> {
            throw new OutboxNonRetryableException("INVALID_EVENT_PAYLOAD");
        });
        permanent.relay.relayOnce();
        assertThat(permanent.repository.transition).isEqualTo("DEAD");
        assertThat(permanent.repository.errorCode).isEqualTo("INVALID_EVENT_PAYLOAD");

        Fixture unsupported = new Fixture(event("event.unsupported", 0), event -> { });
        unsupported.handlers = new OutboxHandlerRegistry(List.of(handler("event.other", e -> { })));
        unsupported.rebuild();
        unsupported.relay.relayOnce();
        assertThat(unsupported.repository.transition).isEqualTo("DEAD");
        assertThat(unsupported.repository.errorCode).isEqualTo("UNSUPPORTED_EVENT_SCHEMA");
        assertThat(unsupported.observer.outcomes)
                .containsExactly(OutboxRelayOutcome.UNSUPPORTED);
    }

    @Test
    void recordsLeaseLossWithoutBlindSecondTransition() {
        Fixture fixture = new Fixture(event("event.ok", 0), event -> { });
        fixture.repository.loseLease = true;

        fixture.relay.relayOnce();

        assertThat(fixture.observer.outcomes)
                .containsExactly(OutboxRelayOutcome.LEASE_LOST);
    }

    private static OutboxEvent event(String type, int retryCount) {
        return new OutboxEvent(
                1, 11, 2, "LOGIN_SESSION", 3, 4,
                type, 1, "{}", retryCount, NOW.minusSeconds(1));
    }

    private static OutboxEventHandler handler(
            String type,
            java.util.function.Consumer<OutboxEvent> consumer) {
        return new OutboxEventHandler() {
            @Override
            public String eventType() {
                return type;
            }

            @Override
            public int schemaVersion() {
                return 1;
            }

            @Override
            public void handle(OutboxEvent event) {
                consumer.accept(event);
            }
        };
    }

    private static final class Fixture {
        private final FakeRepository repository;
        private final RecordingObserver observer = new RecordingObserver();
        private final OutboxRelayProperties properties = properties();
        private OutboxHandlerRegistry handlers;
        private OutboxRelay relay;

        private Fixture(
                OutboxEvent event,
                java.util.function.Consumer<OutboxEvent> consumer) {
            repository = new FakeRepository(event);
            handlers = new OutboxHandlerRegistry(List.of(handler(event.eventType(), consumer)));
            rebuild();
        }

        private void rebuild() {
            relay = new OutboxRelay(
                    repository,
                    handlers,
                    new OutboxRetryPolicy(Duration.ofSeconds(1), Duration.ofMinutes(1)),
                    observer,
                    properties,
                    Clock.fixed(NOW, ZoneOffset.UTC),
                    "test-instance");
        }
    }

    private static OutboxRelayProperties properties() {
        OutboxRelayProperties properties = new OutboxRelayProperties();
        properties.setEnabled(true);
        return properties;
    }

    private static final class FakeRepository implements OutboxRepository {
        private final OutboxEvent event;
        private boolean loseLease;
        private String transition;
        private int retryCount;
        private String errorCode;

        private FakeRepository(OutboxEvent event) {
            this.event = event;
        }

        @Override
        public List<OutboxEvent> claimBatch(
                String owner, Instant now, Instant until, int batch) {
            return List.of(event);
        }

        @Override
        public void markPublished(long id, String owner, Instant at) {
            transition("PUBLISHED", 0, null);
        }

        @Override
        public void reschedule(long id, String owner, int retry, Instant at, String code) {
            transition("PENDING", retry, code);
        }

        @Override
        public void markDead(long id, String owner, int retry, String code) {
            transition("DEAD", retry, code);
        }

        private void transition(String status, int retry, String code) {
            if (loseLease) {
                throw new OutboxLeaseLostException(event.id());
            }
            transition = status;
            retryCount = retry;
            errorCode = code;
        }
    }

    private static final class RecordingObserver implements OutboxRelayObserver {
        private final List<OutboxRelayOutcome> outcomes = new ArrayList<>();

        @Override
        public void claimed(int count) {
        }

        @Override
        public void completed(OutboxRelayOutcome outcome, Duration duration) {
            outcomes.add(outcome);
        }
    }
}

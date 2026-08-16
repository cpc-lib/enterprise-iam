package com.enterprise.iam.auth.infrastructure.outbox;

import com.enterprise.iam.outbox.OutboxEventToAppend;
import com.enterprise.iam.outbox.OutboxWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcSessionProjectionOutboxAppenderTest {

    @Test
    void mapsProjectionAndEventIdentityWithoutStartingTransaction() {
        AtomicReference<OutboxEventToAppend> captured = new AtomicReference<>();
        OutboxWriter writer = captured::set;
        JdbcSessionProjectionOutboxAppender appender =
                new JdbcSessionProjectionOutboxAppender(
                        writer,
                        new SessionProjectionEventCodec(new ObjectMapper()));
        Instant occurredAt = Instant.parse("2026-08-12T12:00:00Z");

        appender.append(101, SessionProjectionEventCodecTest.projection(), occurredAt);

        assertThat(captured.get()).satisfies(event -> {
            assertThat(event.id()).isEqualTo(101);
            assertThat(event.eventId()).isEqualTo(101);
            assertThat(event.tenantId()).isEqualTo(10);
            assertThat(event.aggregateType()).isEqualTo("LOGIN_SESSION");
            assertThat(event.aggregateId()).isEqualTo(30);
            assertThat(event.aggregateVersion()).isEqualTo(5);
            assertThat(event.eventType())
                    .isEqualTo("iam.auth.session-security-projection");
            assertThat(event.schemaVersion()).isEqualTo(1);
            assertThat(event.occurredAt()).isEqualTo(occurredAt);
        });
    }
}

package com.enterprise.iam.auth.infrastructure.outbox;

import com.enterprise.iam.common.mq.DomainEvent;
import com.enterprise.iam.outbox.OutboxEvent;
import com.enterprise.iam.outbox.OutboxEventToAppend;
import com.enterprise.iam.outbox.OutboxNonRetryableException;
import com.enterprise.iam.outbox.OutboxWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginAuditOutboxTest {

    private static final Instant NOW = Instant.parse("2026-10-04T12:30:00Z");
    private final LoginAuditEventCodec codec =
            new LoginAuditEventCodec(new ObjectMapper().findAndRegisterModules());

    @Test
    void appendsSuccessfulLoginWithFrozenMetadataAndNoNullReasonField() {
        AtomicReference<OutboxEventToAppend> captured = new AtomicReference<>();
        OutboxWriter writer = captured::set;
        JdbcLoginAuditOutboxAppender appender =
                new JdbcLoginAuditOutboxAppender(writer, codec);

        appender.appendLoginSucceeded(9001L, 7L, 11L, 101L, "req-1", NOW);

        assertThat(captured.get()).satisfies(event -> {
            assertThat(event.eventType()).isEqualTo("auth.login.succeeded");
            assertThat(event.aggregateType()).isEqualTo("LOGIN_SESSION");
            assertThat(event.aggregateId()).isEqualTo(101L);
            assertThat(event.aggregateVersion()).isEqualTo(1L);
            LoginAuditOutboxPayload payload = codec.decode(event.payload());
            assertThat(payload.requestId()).isEqualTo("req-1");
            assertThat(payload.reason()).isNull();
            assertThat(codec.toMap(payload)).doesNotContainKey("reason");
        });
    }

    @Test
    void appendsPasswordChangedWithCurrentSessionVersionAndFrozenReason() {
        AtomicReference<OutboxEventToAppend> captured = new AtomicReference<>();
        JdbcLoginAuditOutboxAppender appender =
                new JdbcLoginAuditOutboxAppender(captured::set, codec);

        appender.appendPasswordChanged(9002L, 7L, 11L, 101L, 4L, "req-2", NOW);

        assertThat(captured.get()).satisfies(event -> {
            assertThat(event.eventType()).isEqualTo("auth.password.changed");
            assertThat(event.aggregateType()).isEqualTo("LOGIN_SESSION");
            assertThat(event.aggregateId()).isEqualTo(101L);
            assertThat(event.aggregateVersion()).isEqualTo(4L);
            LoginAuditOutboxPayload payload = codec.decode(event.payload());
            assertThat(payload.requestId()).isEqualTo("req-2");
            assertThat(payload.reason()).isEqualTo("PASSWORD_CHANGED");
        });
    }

    @Test
    void publishesValidatedOrphanAuditEventToItsExactRoutingKey() {
        LoginAuditOutboxPayload payload = new LoginAuditOutboxPayload(
                7L,
                11L,
                101L,
                "req-1",
                NOW,
                JdbcLoginAuditOutboxAppender.ORPHAN_REASON);
        AtomicReference<String> routingKey = new AtomicReference<>();
        AtomicReference<DomainEvent> published = new AtomicReference<>();
        LoginAuditOutboxEventHandler handler = new LoginAuditOutboxEventHandler(
                JdbcLoginAuditOutboxAppender.EVENT_TYPE_ORPHAN_RECONCILED,
                codec,
                (exchange, route, event) -> {
                    assertThat(exchange).isEqualTo("iam.domain.events");
                    routingKey.set(route);
                    published.set(event);
                },
                "iam.domain.events");

        handler.handle(event(
                JdbcLoginAuditOutboxAppender.EVENT_TYPE_ORPHAN_RECONCILED,
                7L,
                101L,
                codec.encode(payload)));

        assertThat(routingKey.get())
                .isEqualTo(JdbcLoginAuditOutboxAppender.EVENT_TYPE_ORPHAN_RECONCILED);
        assertThat(published.get().payload())
                .containsEntry("reason", JdbcLoginAuditOutboxAppender.ORPHAN_REASON);
    }

    @Test
    void rejectsPayloadOrEnvelopeMismatchAsNonRetryable() {
        LoginAuditOutboxEventHandler handler = new LoginAuditOutboxEventHandler(
                JdbcLoginAuditOutboxAppender.EVENT_TYPE_LOGIN_SUCCEEDED,
                codec,
                (exchange, route, event) -> { },
                "iam.domain.events");
        LoginAuditOutboxPayload payload = new LoginAuditOutboxPayload(
                7L, 11L, 101L, "req-1", NOW, null);

        assertThatThrownBy(() -> handler.handle(event(
                JdbcLoginAuditOutboxAppender.EVENT_TYPE_LOGIN_SUCCEEDED,
                8L,
                101L,
                codec.encode(payload))))
                .isInstanceOfSatisfying(
                        OutboxNonRetryableException.class,
                        exception -> assertThat(exception.failureCode())
                                .isEqualTo("INVALID_EVENT_METADATA"));
        assertThatThrownBy(() -> handler.handle(event(
                JdbcLoginAuditOutboxAppender.EVENT_TYPE_LOGIN_SUCCEEDED,
                7L,
                101L,
                "{}")))
                .isInstanceOfSatisfying(
                        OutboxNonRetryableException.class,
                        exception -> assertThat(exception.failureCode())
                                .isEqualTo("INVALID_EVENT_PAYLOAD"));
    }

    private static OutboxEvent event(
            String eventType,
            long tenantId,
            long aggregateId,
            String payload) {
        return new OutboxEvent(
                9001L,
                9001L,
                tenantId,
                JdbcLoginAuditOutboxAppender.AGGREGATE_TYPE,
                aggregateId,
                1L,
                eventType,
                LoginAuditOutboxPayload.SCHEMA_VERSION,
                payload,
                0,
                NOW);
    }
}

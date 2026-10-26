package com.enterprise.iam.audit.infrastructure.mq;

import com.enterprise.iam.audit.application.command.RecordSourcedAuditLogCommand;
import com.enterprise.iam.audit.application.service.RecordSourcedAuditLogUseCase;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthLoginAuditEventListenerTest {

    private RecordSourcedAuditLogUseCase useCase;
    private AuthLoginAuditEventListener listener;

    @BeforeEach
    void setUp() {
        useCase = mock(RecordSourcedAuditLogUseCase.class);
        listener = new AuthLoginAuditEventListener(useCase, new ObjectMapper());
    }

    @Test
    void recordsValidLoginSucceededEvent() {
        Message message = message(
                AuthLoginAuditEventListener.EVENT_LOGIN_SUCCEEDED,
                9001L,
                """
                        {
                          "tenantId": 7,
                          "userId": 11,
                          "sessionId": 101,
                          "requestId": "req-1",
                          "occurredAt": "2026-10-04T12:30:00Z"
                        }
                        """);
        when(useCase.record(org.mockito.ArgumentMatchers.any())).thenReturn(true);

        listener.onMessage(message);

        ArgumentCaptor<RecordSourcedAuditLogCommand> command =
                ArgumentCaptor.forClass(RecordSourcedAuditLogCommand.class);
        verify(useCase).record(command.capture());
        assertThat(command.getValue()).isEqualTo(new RecordSourcedAuditLogCommand(
                "iam-auth-service",
                9001L,
                7L,
                11L,
                "LOGIN_SUCCESS",
                "SESSION",
                101L,
                "{\"requestId\":\"req-1\"}",
                Instant.parse("2026-10-04T12:30:00Z")));
    }

    @Test
    void recordsValidOrphanReconciledEventWithReason() {
        Message message = message(
                AuthLoginAuditEventListener.EVENT_ORPHAN_RECONCILED,
                9002L,
                """
                        {
                          "tenantId": 7,
                          "userId": 11,
                          "sessionId": 101,
                          "requestId": "req-1",
                          "occurredAt": "2026-10-04T12:35:00Z",
                          "reason": "LOGIN_DELIVERY_UNCONFIRMED"
                        }
                        """);
        when(useCase.record(org.mockito.ArgumentMatchers.any())).thenReturn(true);

        listener.onMessage(message);

        ArgumentCaptor<RecordSourcedAuditLogCommand> command =
                ArgumentCaptor.forClass(RecordSourcedAuditLogCommand.class);
        verify(useCase).record(command.capture());
        assertThat(command.getValue().action()).isEqualTo("LOGIN_ORPHAN_RECONCILED");
        assertThat(command.getValue().detail())
                .isEqualTo("{\"requestId\":\"req-1\",\"reason\":\"LOGIN_DELIVERY_UNCONFIRMED\"}");
    }

    @Test
    void recordsValidPasswordChangedEventAsPasswordRotate() {
        Message message = message(
                AuthLoginAuditEventListener.EVENT_PASSWORD_CHANGED,
                9003L,
                """
                        {
                          "tenantId": 7,
                          "userId": 11,
                          "sessionId": 101,
                          "requestId": "req-2",
                          "occurredAt": "2026-10-04T12:40:00Z",
                          "reason": "PASSWORD_CHANGED"
                        }
                        """);
        when(useCase.record(org.mockito.ArgumentMatchers.any())).thenReturn(true);

        listener.onMessage(message);

        ArgumentCaptor<RecordSourcedAuditLogCommand> command =
                ArgumentCaptor.forClass(RecordSourcedAuditLogCommand.class);
        verify(useCase).record(command.capture());
        assertThat(command.getValue().action()).isEqualTo("PASSWORD_ROTATE");
        assertThat(command.getValue().detail())
                .isEqualTo("{\"requestId\":\"req-2\",\"reason\":\"PASSWORD_CHANGED\"}");
    }

    @Test
    void acknowledgesDuplicateRedeliveryAfterUseCaseReportsExistingSourceEvent() {
        Message message = message(
                AuthLoginAuditEventListener.EVENT_LOGIN_SUCCEEDED,
                9001L,
                """
                        {
                          "tenantId": 7,
                          "userId": 11,
                          "sessionId": 101,
                          "requestId": "req-1",
                          "occurredAt": "2026-10-04T12:30:00Z"
                        }
                        """);
        when(useCase.record(org.mockito.ArgumentMatchers.any())).thenReturn(true, false);

        assertThatCode(() -> {
            listener.onMessage(message);
            listener.onMessage(message);
        }).doesNotThrowAnyException();

        verify(useCase, org.mockito.Mockito.times(2))
                .record(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsPoisonMessageWithoutRequeue() {
        Message message = message(
                AuthLoginAuditEventListener.EVENT_LOGIN_SUCCEEDED,
                9001L,
                "{not-json");

        assertThatThrownBy(() -> listener.onMessage(message))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verify(useCase, never()).record(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsEnvelopeWhoseTenantDoesNotMatchPayload() {
        Message message = message(
                AuthLoginAuditEventListener.EVENT_LOGIN_SUCCEEDED,
                9001L,
                """
                        {
                          "tenantId": 8,
                          "userId": 11,
                          "sessionId": 101,
                          "requestId": "req-1",
                          "occurredAt": "2026-10-04T12:30:00Z"
                        }
                        """);

        assertThatThrownBy(() -> listener.onMessage(message))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verify(useCase, never()).record(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsOrphanEventWithoutFrozenReason() {
        Message message = message(
                AuthLoginAuditEventListener.EVENT_ORPHAN_RECONCILED,
                9002L,
                """
                        {
                          "tenantId": 7,
                          "userId": 11,
                          "sessionId": 101,
                          "requestId": "req-1",
                          "occurredAt": "2026-10-04T12:35:00Z"
                        }
                        """);

        assertThatThrownBy(() -> listener.onMessage(message))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verify(useCase, never()).record(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsPasswordChangedEventWithoutFrozenReason() {
        Message message = message(
                AuthLoginAuditEventListener.EVENT_PASSWORD_CHANGED,
                9003L,
                """
                        {
                          "tenantId": 7,
                          "userId": 11,
                          "sessionId": 101,
                          "requestId": "req-2",
                          "occurredAt": "2026-10-04T12:40:00Z",
                          "reason": "OTHER"
                        }
                        """);

        assertThatThrownBy(() -> listener.onMessage(message))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verify(useCase, never()).record(org.mockito.ArgumentMatchers.any());
    }

    private static Message message(String eventType, long eventId, String body) {
        MessageProperties properties = new MessageProperties();
        properties.setReceivedRoutingKey(eventType);
        properties.setHeader("iam-event-id", eventId);
        properties.setHeader("iam-event-type", eventType);
        properties.setHeader("iam-schema-version", 1);
        properties.setHeader("iam-tenant-id", 7L);
        properties.setHeader("iam-aggregate-type", "LOGIN_SESSION");
        properties.setHeader("iam-aggregate-id", 101L);
        return new Message(body.getBytes(StandardCharsets.UTF_8), properties);
    }
}

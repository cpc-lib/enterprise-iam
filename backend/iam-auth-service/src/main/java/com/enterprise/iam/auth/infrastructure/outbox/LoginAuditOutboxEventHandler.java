package com.enterprise.iam.auth.infrastructure.outbox;

import com.enterprise.iam.common.mq.DomainEvent;
import com.enterprise.iam.common.mq.DomainEventGateway;
import com.enterprise.iam.outbox.OutboxEvent;
import com.enterprise.iam.outbox.OutboxEventHandler;
import com.enterprise.iam.outbox.OutboxNonRetryableException;

import java.util.Objects;

/** Publishes a validated login audit fact to the durable audit-service queue. */
final class LoginAuditOutboxEventHandler implements OutboxEventHandler {

    private final String eventType;
    private final LoginAuditEventCodec codec;
    private final DomainEventGateway gateway;
    private final String exchange;

    LoginAuditOutboxEventHandler(
            String eventType,
            LoginAuditEventCodec codec,
            DomainEventGateway gateway,
            String exchange) {
        this.eventType = Objects.requireNonNull(eventType, "eventType must not be null");
        this.codec = Objects.requireNonNull(codec, "codec must not be null");
        this.gateway = Objects.requireNonNull(gateway, "gateway must not be null");
        this.exchange = Objects.requireNonNull(exchange, "exchange must not be null");
    }

    @Override
    public String eventType() {
        return eventType;
    }

    @Override
    public int schemaVersion() {
        return LoginAuditOutboxPayload.SCHEMA_VERSION;
    }

    @Override
    public void handle(OutboxEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        LoginAuditOutboxPayload payload;
        try {
            payload = codec.decode(event.payload());
        } catch (LoginAuditEventFormatException exception) {
            throw new OutboxNonRetryableException("INVALID_EVENT_PAYLOAD", exception);
        }
        if (!JdbcLoginAuditOutboxAppender.AGGREGATE_TYPE.equals(event.aggregateType())
                || !eventType.equals(event.eventType())
                || event.tenantId() != payload.tenantId()
                || event.aggregateId() != payload.sessionId()) {
            throw new OutboxNonRetryableException("INVALID_EVENT_METADATA");
        }
        gateway.publish(exchange, eventType, new DomainEvent(
                event.eventId(),
                event.eventType(),
                event.schemaVersion(),
                event.tenantId(),
                event.aggregateType(),
                event.aggregateId(),
                event.createdAt(),
                codec.toMap(payload)));
    }
}

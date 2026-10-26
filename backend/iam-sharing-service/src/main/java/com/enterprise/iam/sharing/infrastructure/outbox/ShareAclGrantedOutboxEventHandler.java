package com.enterprise.iam.sharing.infrastructure.outbox;

import com.enterprise.iam.common.mq.DomainEvent;
import com.enterprise.iam.common.mq.DomainEventGateway;
import com.enterprise.iam.outbox.OutboxEvent;
import com.enterprise.iam.outbox.OutboxEventHandler;
import com.enterprise.iam.outbox.OutboxNonRetryableException;

import java.util.Map;
import java.util.Objects;

/** Idempotent relay handler that republishes granted share ACL events to MQ. */
public final class ShareAclGrantedOutboxEventHandler implements OutboxEventHandler {

    private final ShareAclEventCodec codec;
    private final DomainEventGateway gateway;
    private final String exchange;

    public ShareAclGrantedOutboxEventHandler(
            ShareAclEventCodec codec,
            DomainEventGateway gateway,
            String exchange) {
        this.codec = Objects.requireNonNull(codec, "codec must not be null");
        this.gateway = Objects.requireNonNull(gateway, "gateway must not be null");
        this.exchange = Objects.requireNonNull(exchange, "exchange must not be null");
    }

    @Override
    public String eventType() {
        return JdbcShareAclEventAppender.EVENT_TYPE_GRANTED;
    }

    @Override
    public int schemaVersion() {
        return ShareAclEventCodec.SCHEMA_VERSION;
    }

    @Override
    public void handle(OutboxEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        Map<String, Object> payload;
        try {
            payload = codec.decodeGranted(event.payload());
        } catch (ShareAclEventFormatException exception) {
            throw new OutboxNonRetryableException("INVALID_EVENT_PAYLOAD", exception);
        }
        long shareId = ((Number) payload.get("shareId")).longValue();
        long tenantId = ((Number) payload.get("tenantId")).longValue();
        long epoch = ((Number) payload.get("epoch")).longValue();
        if (!JdbcShareAclEventAppender.AGGREGATE_TYPE.equals(event.aggregateType())
                || event.tenantId() != tenantId
                || event.aggregateId() != shareId
                || event.aggregateVersion() != epoch) {
            throw new OutboxNonRetryableException("INVALID_EVENT_METADATA");
        }
        gateway.publish(exchange, eventType(), new DomainEvent(
                event.eventId(),
                event.eventType(),
                event.schemaVersion(),
                event.tenantId(),
                event.aggregateType(),
                event.aggregateId(),
                event.createdAt(),
                payload));
    }
}

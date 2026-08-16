package com.enterprise.iam.outbox;

import java.time.Instant;
import java.util.Objects;

/** Event inserted in the same physical transaction as its business mutation. */
public record OutboxEventToAppend(
        long id,
        long eventId,
        long tenantId,
        String aggregateType,
        long aggregateId,
        long aggregateVersion,
        String eventType,
        int schemaVersion,
        String payload,
        Instant occurredAt) {

    public OutboxEventToAppend {
        OutboxEvent.requirePositive(id, "id");
        OutboxEvent.requirePositive(eventId, "eventId");
        OutboxEvent.requirePositive(tenantId, "tenantId");
        aggregateType = OutboxEvent.requireType(aggregateType, "aggregateType");
        OutboxEvent.requirePositive(aggregateId, "aggregateId");
        OutboxEvent.requirePositive(aggregateVersion, "aggregateVersion");
        eventType = OutboxEvent.requireType(eventType, "eventType");
        if (schemaVersion <= 0) {
            throw new IllegalArgumentException("schemaVersion must be positive");
        }
        payload = OutboxEvent.requirePayload(payload);
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}

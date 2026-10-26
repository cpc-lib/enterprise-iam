package com.enterprise.iam.common.mq;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Framework-agnostic domain event envelope handed to the messaging gateway.
 * The payload is the versioned JSON body published as the message content.
 */
public record DomainEvent(
        long eventId,
        String eventType,
        int schemaVersion,
        long tenantId,
        String aggregateType,
        long aggregateId,
        Instant occurredAt,
        Map<String, Object> payload) {

    public DomainEvent {
        if (eventId <= 0) {
            throw new IllegalArgumentException("eventId must be positive");
        }
        eventType = requireType(eventType, "eventType");
        if (schemaVersion <= 0) {
            throw new IllegalArgumentException("schemaVersion must be positive");
        }
        if (tenantId <= 0) {
            throw new IllegalArgumentException("tenantId must be positive");
        }
        aggregateType = requireType(aggregateType, "aggregateType");
        if (aggregateId <= 0) {
            throw new IllegalArgumentException("aggregateId must be positive");
        }
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }

    private static String requireType(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        String trimmed = value.trim();
        if (trimmed.isEmpty() || trimmed.length() > 128) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return trimmed;
    }
}

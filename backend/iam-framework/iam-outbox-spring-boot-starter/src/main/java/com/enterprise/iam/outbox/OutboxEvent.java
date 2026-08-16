package com.enterprise.iam.outbox;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

/** Immutable claimed event delivered at least once by the relay. */
public record OutboxEvent(
        long id,
        long eventId,
        long tenantId,
        String aggregateType,
        long aggregateId,
        long aggregateVersion,
        String eventType,
        int schemaVersion,
        String payload,
        int retryCount,
        Instant createdAt) {

    public static final int MAX_PAYLOAD_BYTES = 65_536;
    private static final Pattern TYPE = Pattern.compile("^[A-Za-z][A-Za-z0-9._-]{1,127}$");

    public OutboxEvent {
        requirePositive(id, "id");
        requirePositive(eventId, "eventId");
        requirePositive(tenantId, "tenantId");
        aggregateType = requireType(aggregateType, "aggregateType");
        requirePositive(aggregateId, "aggregateId");
        requirePositive(aggregateVersion, "aggregateVersion");
        eventType = requireType(eventType, "eventType");
        if (schemaVersion <= 0) {
            throw new IllegalArgumentException("schemaVersion must be positive");
        }
        payload = requirePayload(payload);
        if (retryCount < 0) {
            throw new IllegalArgumentException("retryCount must not be negative");
        }
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    static String requireType(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (!TYPE.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value;
    }

    static String requirePayload(String value) {
        Objects.requireNonNull(value, "payload must not be null");
        int bytes = value.getBytes(StandardCharsets.UTF_8).length;
        if (value.isBlank() || bytes > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("payload is blank or exceeds the byte limit");
        }
        return value;
    }

    static void requirePositive(long value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}

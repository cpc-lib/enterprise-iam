package com.enterprise.iam.auth.infrastructure.outbox;

import java.time.Instant;
import java.util.Objects;

record LoginAuditOutboxPayload(
        long tenantId,
        long userId,
        long sessionId,
        String requestId,
        Instant occurredAt,
        String reason) {

    static final int SCHEMA_VERSION = 1;

    LoginAuditOutboxPayload {
        requirePositive(tenantId, "tenantId");
        requirePositive(userId, "userId");
        requirePositive(sessionId, "sessionId");
        requestId = requireText(requestId, "requestId", 128);
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        if (reason != null) {
            reason = requireText(reason, "reason", 128);
        }
    }

    private static void requirePositive(long value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static String requireText(String value, String name, int maximumLength) {
        Objects.requireNonNull(value, name + " must not be null");
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > maximumLength) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return normalized;
    }
}

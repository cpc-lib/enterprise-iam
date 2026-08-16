package com.enterprise.iam.common.security.session;

import java.time.Instant;
import java.util.Objects;

/**
 * Authoritative security projection consumed on every protected Gateway request.
 * Instants are millisecond-aligned because Redis stores the frozen wire format as
 * epoch milliseconds.
 */
public record SessionSecurityProjection(
        long tenantId,
        long subjectId,
        long sessionId,
        long tokenVersion,
        long sessionVersion,
        SessionProjectionStatus status,
        Instant idleExpiresAt,
        Instant absoluteExpiresAt) {

    public SessionSecurityProjection {
        requirePositive(tenantId, "tenantId");
        requirePositive(subjectId, "subjectId");
        requirePositive(sessionId, "sessionId");
        requirePositive(tokenVersion, "tokenVersion");
        requirePositive(sessionVersion, "sessionVersion");
        Objects.requireNonNull(status, "status must not be null");
        requireMillisecondPrecision(idleExpiresAt, "idleExpiresAt");
        requireMillisecondPrecision(absoluteExpiresAt, "absoluteExpiresAt");
        if (idleExpiresAt.isAfter(absoluteExpiresAt)) {
            throw new IllegalArgumentException(
                    "idleExpiresAt must not be after absoluteExpiresAt");
        }
    }

    private static void requirePositive(long value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static void requireMillisecondPrecision(Instant value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        long epochMillis = value.toEpochMilli();
        if (epochMillis <= 0) {
            throw new IllegalArgumentException(name + " must be after the Unix epoch");
        }
        if (!value.equals(Instant.ofEpochMilli(epochMillis))) {
            throw new IllegalArgumentException(name + " must have millisecond precision");
        }
    }
}

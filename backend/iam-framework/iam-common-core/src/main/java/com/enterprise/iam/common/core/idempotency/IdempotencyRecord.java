package com.enterprise.iam.common.core.idempotency;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable idempotency record. The combination of (tenantId, idempotencyKey)
 * uniquely identifies a request and must be enforced atomically.
 */
public record IdempotencyRecord(
        long tenantId,
        String idempotencyKey,
        String responseHash,
        Instant createdAt,
        Instant expiresAt) {

    public IdempotencyRecord {
        if (tenantId <= 0) {
            throw new IllegalArgumentException("tenantId must be positive");
        }
        idempotencyKey = normalizeKey(idempotencyKey);
        Objects.requireNonNull(responseHash, "responseHash must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("expiresAt must be after createdAt");
        }
    }

    private static String normalizeKey(String value) {
        Objects.requireNonNull(value, "idempotencyKey must not be null");
        String normalized = value.trim();
        if (normalized.length() < 8 || normalized.length() > 128) {
            throw new IllegalArgumentException("idempotencyKey length must be 8..128");
        }
        return normalized;
    }
}

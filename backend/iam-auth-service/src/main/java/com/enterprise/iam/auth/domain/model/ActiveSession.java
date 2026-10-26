package com.enterprise.iam.auth.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Domain entity representing an active or historical login session.
 */
public record ActiveSession(
        long id,
        long tenantId,
        long sessionId,
        long userId,
        String status,
        String deviceType,
        Instant lastAccessAt,
        Instant idleExpireAt,
        Instant absoluteExpireAt,
        Instant lastStrongAuthAt,
        Instant revokedAt,
        String revokeReason,
        long sessionVersion,
        Instant createdAt,
        Instant updatedAt
) {
    public ActiveSession {
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(lastAccessAt, "lastAccessAt must not be null");
        Objects.requireNonNull(idleExpireAt, "idleExpireAt must not be null");
        Objects.requireNonNull(absoluteExpireAt, "absoluteExpireAt must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }
}

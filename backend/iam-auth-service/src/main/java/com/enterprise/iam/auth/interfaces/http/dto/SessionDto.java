package com.enterprise.iam.auth.interfaces.http.dto;

import com.enterprise.iam.common.web.json.NumericLong;

import java.time.Instant;

public record SessionDto(
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
        @NumericLong long sessionVersion,
        Instant createdAt,
        Instant updatedAt
) {}

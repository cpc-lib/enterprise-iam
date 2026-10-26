package com.enterprise.iam.authorization.infrastructure.persistence.row;

import java.time.Instant;

public record AuthorizationGrantRow(
        long grantId,
        long tenantId,
        long subjectId,
        long resourceId,
        long operationId,
        String effect,
        boolean active,
        Instant startTime,
        Instant expireTime,
        Long teamId) {
}

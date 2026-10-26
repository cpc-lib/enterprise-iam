package com.enterprise.iam.authorization.infrastructure.persistence.row;

import java.time.Instant;

public record ShareProjectionWriteRow(
        long id,
        long tenantId,
        long shareId,
        long resourceId,
        String resourceInstanceKey,
        String targetType,
        long targetId,
        long operationId,
        Instant startTime,
        Instant expireTime,
        long projectionEpoch,
        Instant now,
        String scopeType,
        String folderPath,
        String collectionResourceIdsJson) {

    public ShareProjectionWriteRow {
        scopeType = scopeType == null || scopeType.isBlank() ? "INSTANCE" : scopeType;
    }

    public ShareProjectionWriteRow(
            long id,
            long tenantId,
            long shareId,
            long resourceId,
            String resourceInstanceKey,
            String targetType,
            long targetId,
            long operationId,
            Instant startTime,
            Instant expireTime,
            long projectionEpoch,
            Instant now) {
        this(id, tenantId, shareId, resourceId, resourceInstanceKey, targetType,
                targetId, operationId, startTime, expireTime, projectionEpoch,
                now, "INSTANCE", null, null);
    }
}

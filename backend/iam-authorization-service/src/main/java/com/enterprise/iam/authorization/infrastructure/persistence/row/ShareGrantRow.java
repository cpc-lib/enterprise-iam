package com.enterprise.iam.authorization.infrastructure.persistence.row;

import java.time.Instant;

public record ShareGrantRow(
        long shareId,
        String resourceInstanceKey,
        String targetType,
        long targetId,
        Instant startTime,
        Instant expireTime,
        long projectionEpoch,
        Long teamId,
        String scopeType,
        String folderPath,
        String collectionResourceIdsJson) {

    public ShareGrantRow(
            long shareId,
            String resourceInstanceKey,
            String targetType,
            long targetId,
            Instant startTime,
            Instant expireTime,
            long projectionEpoch,
            Long teamId) {
        this(shareId, resourceInstanceKey, targetType, targetId, startTime, expireTime,
                projectionEpoch, teamId, "INSTANCE", null, null);
    }

    public ShareGrantRow(
            long shareId,
            String resourceInstanceKey,
            String targetType,
            long targetId,
            Instant startTime,
            Instant expireTime,
            long projectionEpoch) {
        this(shareId, resourceInstanceKey, targetType, targetId, startTime, expireTime,
                projectionEpoch, null, "INSTANCE", null, null);
    }
}

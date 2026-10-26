package com.enterprise.iam.authorization.infrastructure.persistence.row;

import java.time.Instant;

public record ShareFieldGrantWriteRow(
        long id,
        long tenantId,
        long shareId,
        long operationId,
        long fieldId,
        boolean readable,
        boolean writable,
        boolean hidden,
        Long maskStrategyId,
        long projectionEpoch,
        Instant now) {
}

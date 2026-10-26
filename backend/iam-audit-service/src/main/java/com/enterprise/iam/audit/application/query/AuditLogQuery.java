package com.enterprise.iam.audit.application.query;

import java.time.Instant;

public record AuditLogQuery(
        long tenantId,
        Long actorId,
        String action,
        Instant from,
        Instant to,
        int limit) {

    public AuditLogQuery {
        if (tenantId <= 0) {
            throw new IllegalArgumentException("tenantId must be positive");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
    }
}

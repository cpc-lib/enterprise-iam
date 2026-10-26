package com.enterprise.iam.audit.interfaces.rest.dto;

import java.time.Instant;

public record AuditLogResponse(
        long id,
        long tenantId,
        long actorId,
        String action,
        String resourceType,
        long resourceId,
        String detail,
        Instant createdAt) {
}

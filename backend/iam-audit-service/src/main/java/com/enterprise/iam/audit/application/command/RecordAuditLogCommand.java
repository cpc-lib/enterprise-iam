package com.enterprise.iam.audit.application.command;

import java.util.Objects;

public record RecordAuditLogCommand(
        long tenantId,
        long actorId,
        String action,
        String resourceType,
        long resourceId,
        String detail,
        String traceId) {

    public RecordAuditLogCommand {
        if (tenantId <= 0) {
            throw new IllegalArgumentException("tenantId must be positive");
        }
        if (actorId <= 0) {
            throw new IllegalArgumentException("actorId must be positive");
        }
        if (resourceId <= 0) {
            throw new IllegalArgumentException("resourceId must be positive");
        }
        Objects.requireNonNull(action, "action must not be null");
        if (action.isBlank()) {
            throw new IllegalArgumentException("action must not be blank");
        }
        Objects.requireNonNull(resourceType, "resourceType must not be null");
        if (resourceType.isBlank()) {
            throw new IllegalArgumentException("resourceType must not be blank");
        }
        Objects.requireNonNull(traceId, "traceId must not be null");
    }
}

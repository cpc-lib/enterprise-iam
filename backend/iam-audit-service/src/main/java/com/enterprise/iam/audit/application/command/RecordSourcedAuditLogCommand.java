package com.enterprise.iam.audit.application.command;

import java.time.Instant;
import java.util.Objects;

public record RecordSourcedAuditLogCommand(
        String sourceService,
        long sourceEventId,
        long tenantId,
        long actorId,
        String action,
        String resourceType,
        long resourceId,
        String detail,
        Instant occurredAt) {

    public RecordSourcedAuditLogCommand {
        requireText(sourceService, "sourceService");
        if (sourceEventId <= 0) {
            throw new IllegalArgumentException("sourceEventId must be positive");
        }
        if (tenantId <= 0) {
            throw new IllegalArgumentException("tenantId must be positive");
        }
        if (actorId <= 0) {
            throw new IllegalArgumentException("actorId must be positive");
        }
        requireText(action, "action");
        requireText(resourceType, "resourceType");
        if (resourceId <= 0) {
            throw new IllegalArgumentException("resourceId must be positive");
        }
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }

    private static void requireText(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}

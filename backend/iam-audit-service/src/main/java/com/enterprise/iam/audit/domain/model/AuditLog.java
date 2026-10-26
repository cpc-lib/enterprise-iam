package com.enterprise.iam.audit.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable audit log entry. The {@code action} is stored as a raw string so
 * that upstream services can submit arbitrary action codes; {@link AuditAction}
 * documents the well-known platform codes.
 */
public final class AuditLog {

    private final long id;
    private final long tenantId;
    private final long actorId;
    private final String action;
    private final String resourceType;
    private final long resourceId;
    private final String detail;
    private final Instant createdAt;

    public AuditLog(
            long id,
            long tenantId,
            long actorId,
            String action,
            String resourceType,
            long resourceId,
            String detail,
            Instant createdAt) {
        if (id <= 0) {
            throw new IllegalArgumentException("id must be positive");
        }
        if (tenantId <= 0) {
            throw new IllegalArgumentException("tenantId must be positive");
        }
        if (actorId <= 0) {
            throw new IllegalArgumentException("actorId must be positive");
        }
        if (resourceId <= 0) {
            throw new IllegalArgumentException("resourceId must be positive");
        }
        this.id = id;
        this.tenantId = tenantId;
        this.actorId = actorId;
        this.action = Objects.requireNonNull(action, "action must not be null");
        this.resourceType = Objects.requireNonNull(resourceType, "resourceType must not be null");
        this.resourceId = resourceId;
        this.detail = detail;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public long id() {
        return id;
    }

    public long tenantId() {
        return tenantId;
    }

    public long actorId() {
        return actorId;
    }

    public String action() {
        return action;
    }

    public String resourceType() {
        return resourceType;
    }

    public long resourceId() {
        return resourceId;
    }

    public String detail() {
        return detail;
    }

    public Instant createdAt() {
        return createdAt;
    }
}

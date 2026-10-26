package com.enterprise.iam.identity.domain.model;

import java.time.Instant;
import java.util.Objects;

public final class Role {

    private final long id;
    private final long tenantId;
    private final String roleCode;
    private final String roleName;
    private final String description;
    private final int priority;
    private final RoleStatus status;
    private final long version;
    private final Instant createdAt;
    private final Instant updatedAt;

    public Role(
            long id,
            long tenantId,
            String roleCode,
            String roleName,
            String description,
            int priority,
            RoleStatus status,
            long version,
            Instant createdAt,
            Instant updatedAt) {
        if (id <= 0 || tenantId <= 0) {
            throw new IllegalArgumentException("id and tenantId must be positive");
        }
        this.id = id;
        this.tenantId = tenantId;
        this.roleCode = Objects.requireNonNull(roleCode, "roleCode must not be null");
        this.roleName = Objects.requireNonNull(roleName, "roleName must not be null");
        this.description = description;
        this.priority = priority;
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public long id() {
        return id;
    }

    public long tenantId() {
        return tenantId;
    }

    public String roleCode() {
        return roleCode;
    }

    public String roleName() {
        return roleName;
    }

    public String description() {
        return description;
    }

    public int priority() {
        return priority;
    }

    public RoleStatus status() {
        return status;
    }

    public long version() {
        return version;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}

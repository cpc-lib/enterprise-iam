package com.enterprise.iam.organization.domain.model;

import java.time.Instant;
import java.util.Objects;

public final class TeamRole {

    private final long id;
    private final long tenantId;
    private final long teamId;
    private final String roleCode;
    private final String roleName;
    private final String description;
    private final TeamRoleStatus status;
    private final long version;
    private final Instant createdAt;
    private final Instant updatedAt;

    public TeamRole(
            long id,
            long tenantId,
            long teamId,
            String roleCode,
            String roleName,
            String description,
            TeamRoleStatus status,
            long version,
            Instant createdAt,
            Instant updatedAt) {
        if (id <= 0 || tenantId <= 0 || teamId <= 0) {
            throw new IllegalArgumentException("id, tenantId and teamId must be positive");
        }
        this.id = id;
        this.tenantId = tenantId;
        this.teamId = teamId;
        this.roleCode = Objects.requireNonNull(roleCode, "roleCode must not be null");
        this.roleName = Objects.requireNonNull(roleName, "roleName must not be null");
        this.description = description;
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

    public long teamId() {
        return teamId;
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

    public TeamRoleStatus status() {
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

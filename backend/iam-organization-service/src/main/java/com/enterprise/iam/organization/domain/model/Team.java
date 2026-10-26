package com.enterprise.iam.organization.domain.model;

import java.time.Instant;
import java.util.Objects;

public final class Team {

    private final long id;
    private final long tenantId;
    private final long organizationId;
    private final Long parentTeamId;
    private final String teamCode;
    private final String teamName;
    private final String materializedPath;
    private final int depth;
    private final TeamStatus status;
    private final long version;
    private final Instant createdAt;
    private final Instant updatedAt;

    public Team(
            long id,
            long tenantId,
            long organizationId,
            Long parentTeamId,
            String teamCode,
            String teamName,
            String materializedPath,
            int depth,
            TeamStatus status,
            long version,
            Instant createdAt,
            Instant updatedAt) {
        if (id <= 0 || tenantId <= 0 || organizationId <= 0) {
            throw new IllegalArgumentException("id, tenantId and organizationId must be positive");
        }
        if (parentTeamId != null && parentTeamId <= 0) {
            throw new IllegalArgumentException("parentTeamId must be positive when present");
        }
        if (depth < 0 || depth > 32) {
            throw new IllegalArgumentException("depth must be 0..32");
        }
        this.id = id;
        this.tenantId = tenantId;
        this.organizationId = organizationId;
        this.parentTeamId = parentTeamId;
        this.teamCode = Objects.requireNonNull(teamCode, "teamCode must not be null");
        this.teamName = Objects.requireNonNull(teamName, "teamName must not be null");
        this.materializedPath = Objects.requireNonNull(materializedPath, "materializedPath must not be null");
        this.depth = depth;
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

    public long organizationId() {
        return organizationId;
    }

    public Long parentTeamId() {
        return parentTeamId;
    }

    public String teamCode() {
        return teamCode;
    }

    public String teamName() {
        return teamName;
    }

    public String materializedPath() {
        return materializedPath;
    }

    public int depth() {
        return depth;
    }

    public TeamStatus status() {
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

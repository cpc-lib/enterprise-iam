package com.enterprise.iam.organization.domain.model;

import java.time.Instant;
import java.util.Objects;

public final class TeamMemberRole {

    private final long id;
    private final long tenantId;
    private final long teamId;
    private final long userId;
    private final long teamRoleId;
    private final TeamMemberRoleStatus status;
    private final Instant startTime;
    private final Instant expireTime;
    private final long version;

    public TeamMemberRole(
            long id,
            long tenantId,
            long teamId,
            long userId,
            long teamRoleId,
            TeamMemberRoleStatus status,
            Instant startTime,
            Instant expireTime,
            long version) {
        if (id <= 0 || tenantId <= 0 || teamId <= 0 || userId <= 0 || teamRoleId <= 0) {
            throw new IllegalArgumentException("ids must be positive");
        }
        this.id = id;
        this.tenantId = tenantId;
        this.teamId = teamId;
        this.userId = userId;
        this.teamRoleId = teamRoleId;
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.startTime = Objects.requireNonNull(startTime, "startTime must not be null");
        this.expireTime = expireTime;
        this.version = version;
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

    public long userId() {
        return userId;
    }

    public long teamRoleId() {
        return teamRoleId;
    }

    public TeamMemberRoleStatus status() {
        return status;
    }

    public Instant startTime() {
        return startTime;
    }

    public Instant expireTime() {
        return expireTime;
    }

    public long version() {
        return version;
    }
}

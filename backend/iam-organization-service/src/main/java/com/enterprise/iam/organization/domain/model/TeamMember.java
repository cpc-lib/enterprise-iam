package com.enterprise.iam.organization.domain.model;

import java.time.Instant;
import java.util.Objects;

public final class TeamMember {

    private final long id;
    private final long tenantId;
    private final long teamId;
    private final long userId;
    private final TeamMemberStatus membershipStatus;
    private final Instant joinedAt;
    private final Instant leftAt;
    private final long version;

    public TeamMember(
            long id,
            long tenantId,
            long teamId,
            long userId,
            TeamMemberStatus membershipStatus,
            Instant joinedAt,
            Instant leftAt,
            long version) {
        if (id <= 0 || tenantId <= 0 || teamId <= 0 || userId <= 0) {
            throw new IllegalArgumentException("ids must be positive");
        }
        this.id = id;
        this.tenantId = tenantId;
        this.teamId = teamId;
        this.userId = userId;
        this.membershipStatus = Objects.requireNonNull(membershipStatus, "membershipStatus must not be null");
        this.joinedAt = Objects.requireNonNull(joinedAt, "joinedAt must not be null");
        this.leftAt = leftAt;
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

    public TeamMemberStatus membershipStatus() {
        return membershipStatus;
    }

    public Instant joinedAt() {
        return joinedAt;
    }

    public Instant leftAt() {
        return leftAt;
    }

    public long version() {
        return version;
    }
}

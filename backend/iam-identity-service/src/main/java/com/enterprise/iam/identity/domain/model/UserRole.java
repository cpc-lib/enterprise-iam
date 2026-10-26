package com.enterprise.iam.identity.domain.model;

import java.time.Instant;
import java.util.Objects;

public final class UserRole {

    public enum Status {
        ACTIVE,
        REVOKED,
        EXPIRED
    }

    private final long id;
    private final long tenantId;
    private final long userId;
    private final long roleId;
    private final Status status;
    private final Instant startTime;
    private final Instant expireTime;
    private final long version;

    public UserRole(
            long id,
            long tenantId,
            long userId,
            long roleId,
            Status status,
            Instant startTime,
            Instant expireTime,
            long version) {
        if (id <= 0 || tenantId <= 0 || userId <= 0 || roleId <= 0) {
            throw new IllegalArgumentException("ids must be positive");
        }
        this.id = id;
        this.tenantId = tenantId;
        this.userId = userId;
        this.roleId = roleId;
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

    public long userId() {
        return userId;
    }

    public long roleId() {
        return roleId;
    }

    public Status status() {
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

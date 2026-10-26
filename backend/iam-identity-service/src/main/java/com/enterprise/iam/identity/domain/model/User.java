package com.enterprise.iam.identity.domain.model;

import java.time.Instant;
import java.util.Objects;

public final class User {

    private final long id;
    private final long tenantId;
    private final String username;
    private final String displayName;
    private final String email;
    private final String phone;
    private final Long departmentId;
    private final Long positionId;
    private final UserStatus status;
    private final long version;
    private final Instant createdAt;
    private final Instant updatedAt;

    public User(
            long id,
            long tenantId,
            String username,
            String displayName,
            String email,
            String phone,
            UserStatus status,
            long version,
            Instant createdAt,
            Instant updatedAt) {
        this(id, tenantId, username, displayName, email, phone, null, null, status, version, createdAt, updatedAt);
    }

    public User(
            long id,
            long tenantId,
            String username,
            String displayName,
            String email,
            String phone,
            Long departmentId,
            Long positionId,
            UserStatus status,
            long version,
            Instant createdAt,
            Instant updatedAt) {
        if (id <= 0 || tenantId <= 0) {
            throw new IllegalArgumentException("id and tenantId must be positive");
        }
        if (departmentId != null && departmentId <= 0) {
            throw new IllegalArgumentException("departmentId must be positive when present");
        }
        if (positionId != null && positionId <= 0) {
            throw new IllegalArgumentException("positionId must be positive when present");
        }
        this.id = id;
        this.tenantId = tenantId;
        this.username = username;
        this.displayName = Objects.requireNonNull(displayName, "displayName must not be null");
        this.email = email;
        this.phone = phone;
        this.departmentId = departmentId;
        this.positionId = positionId;
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

    public String username() {
        return username;
    }

    public String displayName() {
        return displayName;
    }

    public String email() {
        return email;
    }

    public String phone() {
        return phone;
    }

    public Long departmentId() {
        return departmentId;
    }

    public Long positionId() {
        return positionId;
    }

    public UserStatus status() {
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

package com.enterprise.iam.organization.domain.model;

import java.time.Instant;
import java.util.Objects;

public final class Position {

    private final long id;
    private final long tenantId;
    private final String positionCode;
    private final String positionName;
    private final Long organizationId;
    private final int sortOrder;
    private final String description;
    private final PositionStatus status;
    private final long version;
    private final Instant createdAt;
    private final Instant updatedAt;

    public Position(
            long id,
            long tenantId,
            String positionCode,
            String positionName,
            Long organizationId,
            int sortOrder,
            String description,
            PositionStatus status,
            long version,
            Instant createdAt,
            Instant updatedAt) {
        if (id <= 0 || tenantId <= 0) {
            throw new IllegalArgumentException("id and tenantId must be positive");
        }
        if (organizationId != null && organizationId <= 0) {
            throw new IllegalArgumentException("organizationId must be positive when present");
        }
        this.id = id;
        this.tenantId = tenantId;
        this.positionCode = Objects.requireNonNull(positionCode, "positionCode must not be null");
        this.positionName = Objects.requireNonNull(positionName, "positionName must not be null");
        this.organizationId = organizationId;
        this.sortOrder = sortOrder;
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

    public String positionCode() {
        return positionCode;
    }

    public String positionName() {
        return positionName;
    }

    public Long organizationId() {
        return organizationId;
    }

    public int sortOrder() {
        return sortOrder;
    }

    public String description() {
        return description;
    }

    public PositionStatus status() {
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

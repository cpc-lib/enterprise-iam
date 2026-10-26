package com.enterprise.iam.organization.domain.model;

import java.time.Instant;
import java.util.Objects;

public final class Organization {

    private final long id;
    private final long tenantId;
    private final String orgCode;
    private final String orgName;
    private final Long parentId;
    private final String materializedPath;
    private final int depth;
    private final OrganizationStatus status;
    private final long version;
    private final Instant createdAt;
    private final Instant updatedAt;

    public Organization(
            long id,
            long tenantId,
            String orgCode,
            String orgName,
            Long parentId,
            String materializedPath,
            int depth,
            OrganizationStatus status,
            long version,
            Instant createdAt,
            Instant updatedAt) {
        if (id <= 0 || tenantId <= 0) {
            throw new IllegalArgumentException("id and tenantId must be positive");
        }
        if (parentId != null && parentId <= 0) {
            throw new IllegalArgumentException("parentId must be positive when present");
        }
        if (depth < 0 || depth > 32) {
            throw new IllegalArgumentException("depth must be 0..32");
        }
        this.id = id;
        this.tenantId = tenantId;
        this.orgCode = Objects.requireNonNull(orgCode, "orgCode must not be null");
        this.orgName = Objects.requireNonNull(orgName, "orgName must not be null");
        this.parentId = parentId;
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

    public String orgCode() {
        return orgCode;
    }

    public String orgName() {
        return orgName;
    }

    public Long parentId() {
        return parentId;
    }

    public String materializedPath() {
        return materializedPath;
    }

    public int depth() {
        return depth;
    }

    public OrganizationStatus status() {
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

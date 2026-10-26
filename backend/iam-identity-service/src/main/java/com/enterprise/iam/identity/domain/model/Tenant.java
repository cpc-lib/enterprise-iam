package com.enterprise.iam.identity.domain.model;

import java.time.Instant;
import java.util.Objects;

public final class Tenant {

    private final long id;
    private final String tenantCode;
    private final String tenantName;
    private final TenantStatus status;
    private final long version;
    private final Instant createdAt;
    private final Instant updatedAt;

    public Tenant(
            long id,
            String tenantCode,
            String tenantName,
            TenantStatus status,
            long version,
            Instant createdAt,
            Instant updatedAt) {
        if (id <= 0) {
            throw new IllegalArgumentException("id must be positive");
        }
        this.id = id;
        this.tenantCode = Objects.requireNonNull(tenantCode, "tenantCode must not be null");
        this.tenantName = Objects.requireNonNull(tenantName, "tenantName must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public long id() {
        return id;
    }

    public String tenantCode() {
        return tenantCode;
    }

    public String tenantName() {
        return tenantName;
    }

    public TenantStatus status() {
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

package com.enterprise.iam.sharing.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class ShareAcl {

    private final long id;
    private final long tenantId;
    private final long shareId;
    private final long operationId;
    private final ShareAclStatus status;
    private final long version;
    private final Instant createdAt;
    private final List<ShareFieldPermission> fieldPermissions;

    public ShareAcl(
            long id,
            long tenantId,
            long shareId,
            long operationId,
            ShareAclStatus status,
            long version,
            Instant createdAt,
            List<ShareFieldPermission> fieldPermissions) {
        if (id <= 0 || tenantId <= 0 || shareId <= 0 || operationId <= 0) {
            throw new IllegalArgumentException("id, tenantId, shareId and operationId must be positive");
        }
        this.id = id;
        this.tenantId = tenantId;
        this.shareId = shareId;
        this.operationId = operationId;
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.version = version;
        this.createdAt = createdAt;
        this.fieldPermissions = fieldPermissions == null ? List.of() : List.copyOf(fieldPermissions);
    }

    public long id() {
        return id;
    }

    public long tenantId() {
        return tenantId;
    }

    public long shareId() {
        return shareId;
    }

    public long operationId() {
        return operationId;
    }

    public ShareAclStatus status() {
        return status;
    }

    public long version() {
        return version;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public List<ShareFieldPermission> fieldPermissions() {
        return fieldPermissions;
    }

    public record ShareFieldPermission(
            long fieldId,
            boolean readable,
            boolean writable,
            boolean hidden,
            Long maskStrategyId) {

        public ShareFieldPermission {
            if (fieldId <= 0) {
                throw new IllegalArgumentException("fieldId must be positive");
            }
            if (hidden && (readable || writable)) {
                throw new IllegalArgumentException("hidden field cannot be readable or writable");
            }
        }
    }
}

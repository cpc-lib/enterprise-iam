package com.enterprise.iam.sharing.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class Share {

    private final long id;
    private final long tenantId;
    private final long resourceId;
    private final String resourceInstanceKey;
    private final ShareTargetType targetType;
    private final long targetId;
    private final long creatorUserId;
    private final Long parentShareId;
    private final long rootShareId;
    private final int shareDepth;
    private final ShareStatus status;
    private final Instant startTime;
    private final Instant expireTime;
    private final boolean canReshare;
    private final Integer activeSlot;
    private final String revokeReason;
    private final Long revokedBy;
    private final Instant revokedAt;
    private final long version;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final ShareScopeType scopeType;
    private final String folderPath;
    private final List<String> collectionResourceIds;

    public Share(
            long id,
            long tenantId,
            long resourceId,
            String resourceInstanceKey,
            ShareTargetType targetType,
            long targetId,
            long creatorUserId,
            Long parentShareId,
            long rootShareId,
            int shareDepth,
            ShareStatus status,
            Instant startTime,
            Instant expireTime,
            boolean canReshare,
            Integer activeSlot,
            String revokeReason,
            Long revokedBy,
            Instant revokedAt,
            long version,
            Instant createdAt,
            Instant updatedAt) {
        this(id, tenantId, resourceId, resourceInstanceKey, targetType, targetId,
                creatorUserId, parentShareId, rootShareId, shareDepth, status,
                startTime, expireTime, canReshare, activeSlot, revokeReason,
                revokedBy, revokedAt, version, createdAt, updatedAt,
                ShareScopeType.INSTANCE, null, List.of());
    }

    public Share(
            long id,
            long tenantId,
            long resourceId,
            String resourceInstanceKey,
            ShareTargetType targetType,
            long targetId,
            long creatorUserId,
            Long parentShareId,
            long rootShareId,
            int shareDepth,
            ShareStatus status,
            Instant startTime,
            Instant expireTime,
            boolean canReshare,
            Integer activeSlot,
            String revokeReason,
            Long revokedBy,
            Instant revokedAt,
            long version,
            Instant createdAt,
            Instant updatedAt,
            ShareScopeType scopeType,
            String folderPath,
            List<String> collectionResourceIds) {
        if (id <= 0 || tenantId <= 0) {
            throw new IllegalArgumentException("id and tenantId must be positive");
        }
        if (resourceId <= 0 || targetId <= 0 || creatorUserId <= 0 || rootShareId <= 0) {
            throw new IllegalArgumentException("resourceId, targetId, creatorUserId and rootShareId must be positive");
        }
        this.id = id;
        this.tenantId = tenantId;
        this.resourceId = resourceId;
        this.resourceInstanceKey = Objects.requireNonNull(resourceInstanceKey, "resourceInstanceKey must not be null");
        this.targetType = Objects.requireNonNull(targetType, "targetType must not be null");
        this.targetId = targetId;
        this.creatorUserId = creatorUserId;
        this.parentShareId = parentShareId;
        this.rootShareId = rootShareId;
        this.shareDepth = shareDepth;
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.startTime = Objects.requireNonNull(startTime, "startTime must not be null");
        this.expireTime = Objects.requireNonNull(expireTime, "expireTime must not be null");
        this.canReshare = canReshare;
        this.activeSlot = activeSlot;
        this.revokeReason = revokeReason;
        this.revokedBy = revokedBy;
        this.revokedAt = revokedAt;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.scopeType = scopeType == null ? ShareScopeType.INSTANCE : scopeType;
        this.folderPath = folderPath;
        this.collectionResourceIds = collectionResourceIds == null
                ? List.of() : List.copyOf(collectionResourceIds);
    }

    public long id() {
        return id;
    }

    public long tenantId() {
        return tenantId;
    }

    public long resourceId() {
        return resourceId;
    }

    public String resourceInstanceKey() {
        return resourceInstanceKey;
    }

    public ShareTargetType targetType() {
        return targetType;
    }

    public long targetId() {
        return targetId;
    }

    public long creatorUserId() {
        return creatorUserId;
    }

    public Long parentShareId() {
        return parentShareId;
    }

    public long rootShareId() {
        return rootShareId;
    }

    public int shareDepth() {
        return shareDepth;
    }

    public ShareStatus status() {
        return status;
    }

    public Instant startTime() {
        return startTime;
    }

    public Instant expireTime() {
        return expireTime;
    }

    public boolean canReshare() {
        return canReshare;
    }

    public Integer activeSlot() {
        return activeSlot;
    }

    public String revokeReason() {
        return revokeReason;
    }

    public Long revokedBy() {
        return revokedBy;
    }

    public Instant revokedAt() {
        return revokedAt;
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

    public ShareScopeType scopeType() {
        return scopeType;
    }

    public String folderPath() {
        return folderPath;
    }

    public List<String> collectionResourceIds() {
        return collectionResourceIds;
    }
}

package com.enterprise.iam.sharing.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

public interface SharingPersistenceMapper {

    int insertShare(
            @Param("id") long id,
            @Param("tenantId") long tenantId,
            @Param("resourceId") long resourceId,
            @Param("resourceInstanceKey") String resourceInstanceKey,
            @Param("targetType") String targetType,
            @Param("targetId") long targetId,
            @Param("creatorUserId") long creatorUserId,
            @Param("parentShareId") Long parentShareId,
            @Param("rootShareId") long rootShareId,
            @Param("shareDepth") int shareDepth,
            @Param("status") String status,
            @Param("startTime") Instant startTime,
            @Param("expireTime") Instant expireTime,
            @Param("canReshare") boolean canReshare,
            @Param("activeSlot") Integer activeSlot,
            @Param("revokeReason") String revokeReason,
            @Param("revokedBy") Long revokedBy,
            @Param("revokedAt") Instant revokedAt,
            @Param("version") long version,
            @Param("createdAt") Instant createdAt,
            @Param("updatedAt") Instant updatedAt,
            @Param("scopeType") String scopeType,
            @Param("folderPath") String folderPath,
            @Param("collectionResourceIdsJson") String collectionResourceIdsJson);

    default int insertShare(
            long id,
            long tenantId,
            long resourceId,
            String resourceInstanceKey,
            String targetType,
            long targetId,
            long creatorUserId,
            Long parentShareId,
            long rootShareId,
            int shareDepth,
            String status,
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
        return insertShare(id, tenantId, resourceId, resourceInstanceKey, targetType,
                targetId, creatorUserId, parentShareId, rootShareId, shareDepth,
                status, startTime, expireTime, canReshare, activeSlot, revokeReason,
                revokedBy, revokedAt, version, createdAt, updatedAt,
                "INSTANCE", null, null);
    }

    ShareRow findShareById(
            @Param("tenantId") long tenantId,
            @Param("shareId") long shareId);

    int revokeShare(
            @Param("tenantId") long tenantId,
            @Param("shareId") long shareId,
            @Param("status") String status,
            @Param("reason") String reason,
            @Param("expectedVersion") long expectedVersion,
            @Param("now") Instant now);

    int insertShareOperation(
            @Param("id") long id,
            @Param("tenantId") long tenantId,
            @Param("shareId") long shareId,
            @Param("operationId") long operationId,
            @Param("version") long version,
            @Param("createdAt") Instant createdAt);

    int insertShareField(
            @Param("id") long id,
            @Param("tenantId") long tenantId,
            @Param("shareId") long shareId,
            @Param("operationId") long operationId,
            @Param("fieldId") long fieldId,
            @Param("readable") boolean readable,
            @Param("writable") boolean writable,
            @Param("hidden") boolean hidden,
            @Param("maskStrategyId") Long maskStrategyId,
            @Param("createdAt") Instant createdAt);

    List<ShareAclOperationRow> findShareAclOperations(
            @Param("tenantId") long tenantId,
            @Param("shareId") long shareId);

    List<ShareFieldPermissionRow> findShareFields(
            @Param("tenantId") long tenantId,
            @Param("shareId") long shareId,
            @Param("operationId") long operationId);

    int upsertShareProjectionEpoch(
            @Param("id") long id,
            @Param("tenantId") long tenantId,
            @Param("resourceId") long resourceId,
            @Param("initialEpoch") long initialEpoch,
            @Param("now") Instant now);

    Long findShareProjectionEpoch(
            @Param("tenantId") long tenantId,
            @Param("resourceId") long resourceId);

    int advanceExpectedEpoch(
            @Param("tenantId") long tenantId,
            @Param("resourceId") long resourceId,
            @Param("epoch") long epoch,
            @Param("now") Instant now);

    record ShareRow(
            long id,
            long tenantId,
            long resourceId,
            String resourceInstanceKey,
            String targetType,
            long targetId,
            long creatorUserId,
            Long parentShareId,
            long rootShareId,
            int shareDepth,
            String status,
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
            String scopeType,
            String folderPath,
            String collectionResourceIdsJson) {

        public ShareRow(
                long id,
                long tenantId,
                long resourceId,
                String resourceInstanceKey,
                String targetType,
                long targetId,
                long creatorUserId,
                Long parentShareId,
                long rootShareId,
                int shareDepth,
                String status,
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
                    "INSTANCE", null, null);
        }
    }

    record ShareAclOperationRow(
            long id,
            long tenantId,
            long shareId,
            long operationId,
            long version,
            Instant createdAt) {
    }

    record ShareFieldPermissionRow(
            long fieldId,
            boolean readable,
            boolean writable,
            boolean hidden,
            Long maskStrategyId) {
    }
}

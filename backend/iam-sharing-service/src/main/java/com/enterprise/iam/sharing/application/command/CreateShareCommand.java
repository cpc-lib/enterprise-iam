package com.enterprise.iam.sharing.application.command;

import com.enterprise.iam.sharing.domain.model.ShareScopeType;
import com.enterprise.iam.sharing.domain.model.ShareTargetType;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Application command for creating a share. The start time is assigned by the
 * use case from its clock; {@code parentShareId} is non-null only for reshared
 * children. V2.0 adds optional {@link ShareScopeType}, {@code folderPath}, and
 * {@code collectionResourceIds}.
 */
public record CreateShareCommand(
        long tenantId,
        long resourceId,
        String resourceInstanceKey,
        ShareTargetType targetType,
        long targetId,
        long creatorUserId,
        Long parentShareId,
        boolean canReshare,
        Instant expireAt,
        List<Long> operationIds,
        List<FieldGrant> fields,
        String requestId,
        ShareScopeType scopeType,
        String folderPath,
        List<String> collectionResourceIds) {

    public CreateShareCommand {
        if (tenantId <= 0 || resourceId <= 0 || targetId <= 0 || creatorUserId <= 0) {
            throw new IllegalArgumentException("tenantId, resourceId, targetId and creatorUserId must be positive");
        }
        Objects.requireNonNull(resourceInstanceKey, "resourceInstanceKey must not be null");
        Objects.requireNonNull(targetType, "targetType must not be null");
        Objects.requireNonNull(expireAt, "expireAt must not be null");
        Objects.requireNonNull(operationIds, "operationIds must not be null");
        if (operationIds.isEmpty()) {
            throw new IllegalArgumentException("operationIds must not be empty");
        }
        for (Long operationId : operationIds) {
            if (operationId == null || operationId <= 0) {
                throw new IllegalArgumentException("operationIds must contain positive values");
            }
        }
        Objects.requireNonNull(fields, "fields must not be null");
        for (FieldGrant field : fields) {
            Objects.requireNonNull(field, "field grant must not be null");
        }
        Objects.requireNonNull(requestId, "requestId must not be null");
        operationIds = List.copyOf(operationIds);
        fields = List.copyOf(fields);
        scopeType = scopeType == null ? ShareScopeType.INSTANCE : scopeType;
        collectionResourceIds = collectionResourceIds == null
                ? List.of() : List.copyOf(collectionResourceIds);
    }

    /** Backward-compatible constructor defaulting to INSTANCE scope. */
    public CreateShareCommand(
            long tenantId,
            long resourceId,
            String resourceInstanceKey,
            ShareTargetType targetType,
            long targetId,
            long creatorUserId,
            Long parentShareId,
            boolean canReshare,
            Instant expireAt,
            List<Long> operationIds,
            List<FieldGrant> fields,
            String requestId) {
        this(tenantId, resourceId, resourceInstanceKey, targetType, targetId,
                creatorUserId, parentShareId, canReshare, expireAt, operationIds,
                fields, requestId, ShareScopeType.INSTANCE, null, List.of());
    }

    /**
     * One persisted field grant for an operation of this share. The
     * hidden/readable/writable cross-invariant is enforced by the use case so
     * callers receive the contract's 422 response.
     */
    public record FieldGrant(
            long operationId,
            long fieldId,
            boolean readable,
            boolean writable,
            boolean hidden,
            Long maskStrategyId) {

        public FieldGrant {
            if (operationId <= 0 || fieldId <= 0) {
                throw new IllegalArgumentException("operationId and fieldId must be positive");
            }
            if (maskStrategyId != null && maskStrategyId <= 0) {
                throw new IllegalArgumentException("maskStrategyId must be positive");
            }
        }
    }
}

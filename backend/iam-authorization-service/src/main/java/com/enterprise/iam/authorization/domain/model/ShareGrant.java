package com.enterprise.iam.authorization.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * A share-derived grant materialized from the sharing-service ACL projection.
 * The engine revalidates tenant/resource/operation/instance/time boundaries
 * and resolves USER/TEAM/ROLE/TEAM_ROLE target membership itself; the projection
 * is never trusted blindly.
 *
 * <p>{@code teamId} is non-null for TEAM_ROLE shares expanded through
 * {@code iam_team_member_role}; the grant matches only when the request
 * context carries that team (fail-closed if the subject has left the team).
 *
 * <p>V2.0 extends matching with {@link ShareScopeType#FOLDER_INHERITED}
 * (prefix match against {@code folderPath} in context or resourceInstanceId)
 * and {@link ShareScopeType#COLLECTION} (set membership in {@code collectionResourceIds}).
 */
public record ShareGrant(
        long shareId,
        long tenantId,
        long resourceId,
        String resourceInstanceKey,
        String targetType,
        long targetId,
        long operationId,
        Instant startTime,
        Instant expireTime,
        long epoch,
        List<ShareFieldGrant> fieldGrants,
        Long teamId,
        ShareScopeType scopeType,
        String folderPath,
        List<String> collectionResourceIds) {

    public static final String TARGET_USER = "USER";
    public static final String TARGET_TEAM = "TEAM";
    public static final String TARGET_ROLE = "ROLE";
    public static final String TARGET_TEAM_ROLE = "TEAM_ROLE";

    public ShareGrant {
        if (shareId <= 0 || tenantId <= 0 || resourceId <= 0 || targetId <= 0 || operationId <= 0) {
            throw new IllegalArgumentException("share grant identifiers must be positive");
        }
        Objects.requireNonNull(resourceInstanceKey, "resourceInstanceKey must not be null");
        Objects.requireNonNull(targetType, "targetType must not be null");
        if (!TARGET_USER.equals(targetType)
                && !TARGET_TEAM.equals(targetType)
                && !TARGET_ROLE.equals(targetType)
                && !TARGET_TEAM_ROLE.equals(targetType)) {
            throw new IllegalArgumentException("targetType must be USER, TEAM, ROLE, or TEAM_ROLE");
        }
        if (teamId != null && teamId <= 0) {
            throw new IllegalArgumentException("teamId must be positive or null");
        }
        Objects.requireNonNull(startTime, "startTime must not be null");
        Objects.requireNonNull(expireTime, "expireTime must not be null");
        if (!expireTime.isAfter(startTime)) {
            throw new IllegalArgumentException("expireTime must be after startTime");
        }
        if (epoch < 0) {
            throw new IllegalArgumentException("epoch must not be negative");
        }
        fieldGrants = fieldGrants == null ? List.of() : List.copyOf(fieldGrants);
        scopeType = scopeType == null ? ShareScopeType.INSTANCE : scopeType;
        collectionResourceIds = collectionResourceIds == null
                ? List.of() : List.copyOf(collectionResourceIds);
    }

    /** Backward-compatible constructor with teamId (defaults to INSTANCE scope). */
    public ShareGrant(
            long shareId,
            long tenantId,
            long resourceId,
            String resourceInstanceKey,
            String targetType,
            long targetId,
            long operationId,
            Instant startTime,
            Instant expireTime,
            long epoch,
            List<ShareFieldGrant> fieldGrants,
            Long teamId) {
        this(shareId, tenantId, resourceId, resourceInstanceKey, targetType, targetId,
                operationId, startTime, expireTime, epoch, fieldGrants, teamId,
                ShareScopeType.INSTANCE, null, List.of());
    }

    /** Backward-compatible constructor without teamId. */
    public ShareGrant(
            long shareId, long tenantId, long resourceId,
            String resourceInstanceKey, String targetType, long targetId,
            long operationId, Instant startTime, Instant expireTime,
            long epoch, List<ShareFieldGrant> fieldGrants) {
        this(shareId, tenantId, resourceId, resourceInstanceKey, targetType, targetId,
                operationId, startTime, expireTime, epoch, fieldGrants, null,
                ShareScopeType.INSTANCE, null, List.of());
    }

    /** Backward-compatible constructor without field grants and teamId. */
    public ShareGrant(
            long shareId, long tenantId, long resourceId,
            String resourceInstanceKey, String targetType, long targetId,
            long operationId, Instant startTime, Instant expireTime, long epoch) {
        this(shareId, tenantId, resourceId, resourceInstanceKey, targetType, targetId,
                operationId, startTime, expireTime, epoch, List.of(), null,
                ShareScopeType.INSTANCE, null, List.of());
    }

    public boolean matches(
            AuthorizationRequest request,
            List<Long> teamIds,
            Instant evaluatedAt) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(teamIds, "teamIds must not be null");
        Objects.requireNonNull(evaluatedAt, "evaluatedAt must not be null");
        if (tenantId != request.tenantId()
                || resourceId != request.resourceId()
                || operationId != request.operationId()) {
            return false;
        }
        if (!matchesResourceScope(request)) {
            return false;
        }
        if (evaluatedAt.isBefore(startTime) || !evaluatedAt.isBefore(expireTime)) {
            return false;
        }
        if (TARGET_USER.equals(targetType)) {
            return targetId == request.subjectId();
        }
        if (TARGET_TEAM.equals(targetType)) {
            return teamIds.contains(targetId);
        }
        if (TARGET_ROLE.equals(targetType)) {
            return true;
        }
        if (TARGET_TEAM_ROLE.equals(targetType)) {
            return teamId != null ? teamIds.contains(teamId) : !teamIds.isEmpty();
        }
        return false;
    }

    private boolean matchesResourceScope(AuthorizationRequest request) {
        if (Objects.equals(resourceInstanceKey, request.resourceInstanceId())) {
            return true;
        }
        if (scopeType == ShareScopeType.FOLDER_INHERITED || (folderPath != null && !folderPath.isBlank())) {
            String normalizedPrefix = folderPath != null && !folderPath.isBlank()
                    ? folderPath.trim()
                    : resourceInstanceKey;
            if (normalizedPrefix != null && !normalizedPrefix.isEmpty()) {
                Object ctxFolder = request.context() != null ? request.context().get("folderPath") : null;
                if (ctxFolder instanceof String ctxFolderStr && matchesFolderPrefix(ctxFolderStr, normalizedPrefix)) {
                    return true;
                }
                if (request.resourceInstanceId() != null
                        && matchesFolderPrefix(request.resourceInstanceId(), normalizedPrefix)) {
                    return true;
                }
            }
        }
        if (scopeType == ShareScopeType.COLLECTION || !collectionResourceIds.isEmpty()) {
            String instanceId = request.resourceInstanceId();
            if (instanceId != null && collectionResourceIds.contains(instanceId)) {
                return true;
            }
            if (collectionResourceIds.contains(String.valueOf(request.resourceId()))) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesFolderPrefix(String candidatePath, String folderPrefix) {
        if (candidatePath == null || folderPrefix == null || folderPrefix.isEmpty()) {
            return false;
        }
        if (candidatePath.equals(folderPrefix)) {
            return true;
        }
        String prefixWithSlash = folderPrefix.endsWith("/") ? folderPrefix : folderPrefix + "/";
        return candidatePath.startsWith(prefixWithSlash);
    }
}

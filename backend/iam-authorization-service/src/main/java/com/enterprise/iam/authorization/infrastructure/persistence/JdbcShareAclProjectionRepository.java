package com.enterprise.iam.authorization.infrastructure.persistence;

import com.enterprise.iam.authorization.application.port.out.ShareAclProjectionRepository;
import com.enterprise.iam.authorization.domain.model.ShareEpoch;
import com.enterprise.iam.authorization.domain.model.ShareFieldGrant;
import com.enterprise.iam.authorization.domain.model.ShareGrant;
import com.enterprise.iam.authorization.domain.model.ShareScopeType;
import com.enterprise.iam.authorization.infrastructure.persistence.mapper.ShareAclProjectionMapper;
import com.enterprise.iam.authorization.infrastructure.persistence.row.ShareEpochRow;
import com.enterprise.iam.authorization.infrastructure.persistence.row.ShareFieldGrantRow;
import com.enterprise.iam.authorization.infrastructure.persistence.row.ShareGrantRow;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Reads time-valid ACTIVE projection rows for the deciding USER and their
 * TEAM memberships, plus the security epoch fence. Field grants for matched
 * shares are loaded in a single follow-up query and attached to the grants.
 */
public final class JdbcShareAclProjectionRepository implements ShareAclProjectionRepository {

    private final ShareAclProjectionMapper mapper;

    public JdbcShareAclProjectionRepository(ShareAclProjectionMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public List<ShareGrant> findActiveGrants(
            long tenantId,
            long subjectUserId,
            List<Long> teamIds,
            long resourceId,
            String resourceInstanceKey,
            long operationId,
            Instant evaluatedAt) {
        List<Long> safeTeamIds = teamIds == null ? List.of() : teamIds;
        List<ShareGrant> grants = mapper.selectActiveGrants(
                        tenantId, subjectUserId, safeTeamIds, resourceId,
                        resourceInstanceKey, operationId, evaluatedAt).stream()
                .map(row -> toShareGrant(row, tenantId, resourceId, operationId))
                .toList();

        if (grants.isEmpty()) {
            return grants;
        }
        Map<Long, List<ShareFieldGrant>> fieldGrantsByShareId =
                loadFieldGrants(tenantId, operationId, grants);
        return grants.stream()
                .map(grant -> new ShareGrant(
                        grant.shareId(), grant.tenantId(), grant.resourceId(),
                        grant.resourceInstanceKey(), grant.targetType(), grant.targetId(),
                        grant.operationId(), grant.startTime(), grant.expireTime(),
                        grant.epoch(),
                        fieldGrantsByShareId.getOrDefault(grant.shareId(), List.of()),
                        grant.teamId(),
                        grant.scopeType(),
                        grant.folderPath(),
                        grant.collectionResourceIds()))
                .toList();
    }

    private Map<Long, List<ShareFieldGrant>> loadFieldGrants(
            long tenantId, long operationId, List<ShareGrant> grants) {
        List<Long> shareIds = grants.stream()
                .map(ShareGrant::shareId)
                .distinct()
                .toList();
        if (shareIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<ShareFieldGrant>> result = new HashMap<>();
        for (ShareFieldGrantRow row : mapper.selectFieldGrants(
                tenantId, shareIds, operationId)) {
            result.computeIfAbsent(row.shareId(), key -> new ArrayList<>()).add(
                    new ShareFieldGrant(
                            row.fieldId(), row.readable(), row.writable(), row.hidden(),
                            row.maskStrategyId()));
        }
        return result;
    }

    @Override
    public ShareEpoch readEpoch(long tenantId, long resourceId) {
        ShareEpochRow row = mapper.selectEpoch(tenantId, resourceId);
        if (row == null) {
            return new ShareEpoch(0L, 0L);
        }
        return new ShareEpoch(row.expectedEpoch(), row.appliedEpoch());
    }

    private static ShareGrant toShareGrant(
            ShareGrantRow row, long tenantId, long resourceId, long operationId) {
        ShareScopeType scopeType = parseScopeType(row.scopeType());
        List<String> collectionIds = parseCollectionIds(row.collectionResourceIdsJson());
        return new ShareGrant(
                row.shareId(), tenantId, resourceId, row.resourceInstanceKey(),
                row.targetType(), row.targetId(), operationId, row.startTime(),
                row.expireTime(), row.projectionEpoch(), List.of(), row.teamId(),
                scopeType, row.folderPath(), collectionIds);
    }

    private static ShareScopeType parseScopeType(String raw) {
        if (raw == null || raw.isBlank()) {
            return ShareScopeType.INSTANCE;
        }
        try {
            return ShareScopeType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return ShareScopeType.INSTANCE;
        }
    }

    private static List<String> parseCollectionIds(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        String trimmed = json.trim();
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
        }
        if (trimmed.isEmpty()) {
            return List.of();
        }
        List<String> items = new ArrayList<>();
        for (String token : trimmed.split(",")) {
            String cleaned = token.trim();
            if ((cleaned.startsWith("\"") && cleaned.endsWith("\""))
                    || (cleaned.startsWith("'") && cleaned.endsWith("'"))) {
                cleaned = cleaned.substring(1, cleaned.length() - 1).trim();
            }
            if (!cleaned.isEmpty()) {
                items.add(cleaned);
            }
        }
        return List.copyOf(items);
    }
}

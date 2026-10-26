package com.enterprise.iam.sharing.infrastructure.persistence;

import com.enterprise.iam.sharing.application.port.out.ShareRepository;
import com.enterprise.iam.sharing.domain.model.Share;
import com.enterprise.iam.sharing.domain.model.ShareScopeType;
import com.enterprise.iam.sharing.domain.model.ShareStatus;
import com.enterprise.iam.sharing.domain.model.ShareTargetType;
import com.enterprise.iam.sharing.infrastructure.persistence.mapper.SharingPersistenceMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class JdbcShareRepository implements ShareRepository {

    private final SharingPersistenceMapper mapper;

    public JdbcShareRepository(SharingPersistenceMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
    }

    @Override
    public Share save(Share share) {
        int updated = mapper.insertShare(
                share.id(),
                share.tenantId(),
                share.resourceId(),
                share.resourceInstanceKey(),
                share.targetType().name(),
                share.targetId(),
                share.creatorUserId(),
                share.parentShareId(),
                share.rootShareId(),
                share.shareDepth(),
                share.status().name(),
                share.startTime(),
                share.expireTime(),
                share.canReshare(),
                share.activeSlot(),
                share.revokeReason(),
                share.revokedBy(),
                share.revokedAt(),
                share.version(),
                share.createdAt(),
                share.updatedAt(),
                share.scopeType().name(),
                share.folderPath(),
                serializeCollectionIds(share.collectionResourceIds()));
        if (updated != 1) {
            throw new IllegalStateException("share insert did not affect exactly one row");
        }
        return share;
    }

    @Override
    public Optional<Share> findById(long tenantId, long shareId) {
        SharingPersistenceMapper.ShareRow row = mapper.findShareById(tenantId, shareId);
        if (row == null) {
            return Optional.empty();
        }
        return Optional.of(new Share(
                row.id(),
                row.tenantId(),
                row.resourceId(),
                row.resourceInstanceKey(),
                ShareTargetType.valueOf(row.targetType()),
                row.targetId(),
                row.creatorUserId(),
                row.parentShareId(),
                row.rootShareId(),
                row.shareDepth(),
                ShareStatus.valueOf(row.status()),
                row.startTime(),
                row.expireTime(),
                row.canReshare(),
                row.activeSlot(),
                row.revokeReason(),
                row.revokedBy(),
                row.revokedAt(),
                row.version(),
                row.createdAt(),
                row.updatedAt(),
                parseScopeType(row.scopeType()),
                row.folderPath(),
                parseCollectionIds(row.collectionResourceIdsJson())));
    }

    @Override
    public void revoke(long tenantId, long shareId, ShareStatus status, String reason,
                       long expectedVersion) {
        Instant now = Instant.now();
        int updated = mapper.revokeShare(
                tenantId, shareId, status.name(), reason, expectedVersion, now);
        if (updated != 1) {
            throw new IllegalStateException("share revoke conflict");
        }
    }

    private static String serializeCollectionIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return null;
        }
        return ids.stream()
                .filter(Objects::nonNull)
                .map(id -> "\"" + id.replace("\"", "\\\"") + "\"")
                .collect(Collectors.joining(",", "[", "]"));
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

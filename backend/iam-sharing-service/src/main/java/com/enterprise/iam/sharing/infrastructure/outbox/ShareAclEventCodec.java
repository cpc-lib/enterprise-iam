package com.enterprise.iam.sharing.infrastructure.outbox;

import com.enterprise.iam.sharing.domain.model.Share;
import com.enterprise.iam.sharing.domain.model.ShareAcl;
import com.enterprise.iam.sharing.domain.model.ShareTargetType;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Versioned JSON contract for {@code share.acl.granted/v1} and
 * {@code share.acl.revoked/v1} outbox payloads. Decoding strictly validates
 * the envelope so malformed or poisoned rows can be dead-lettered.
 */
public final class ShareAclEventCodec {

    static final int SCHEMA_VERSION = 1;
    static final long MIN_EPOCH = 1L;

    private final ObjectMapper mapper;

    public ShareAclEventCodec(ObjectMapper applicationMapper) {
        Objects.requireNonNull(applicationMapper, "applicationMapper must not be null");
        JsonFactory strictFactory = (JsonFactory) applicationMapper.getFactory()
                .rebuild()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .build();
        ObjectMapper strictMapper = applicationMapper.copyWith(strictFactory)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);
        strictMapper.setConfig(strictMapper.getDeserializationConfig()
                .without(MapperFeature.ALLOW_COERCION_OF_SCALARS));
        mapper = strictMapper;
    }

    public String encodeGranted(Share share, List<ShareAcl> acls, long epoch) {
        Objects.requireNonNull(share, "share must not be null");
        Objects.requireNonNull(acls, "acls must not be null");
        if (acls.isEmpty()) {
            throw new IllegalArgumentException("acls must not be empty");
        }
        if (epoch < MIN_EPOCH) {
            throw new IllegalArgumentException("granted epoch must be >= 1");
        }
        List<Long> operationIds = new ArrayList<>();
        List<Map<String, Object>> fieldGrants = new ArrayList<>();
        for (ShareAcl acl : acls) {
            operationIds.add(acl.operationId());
            for (ShareAcl.ShareFieldPermission fp : acl.fieldPermissions()) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("operationId", acl.operationId());
                entry.put("fieldId", fp.fieldId());
                entry.put("readable", fp.readable());
                entry.put("writable", fp.writable());
                entry.put("hidden", fp.hidden());
                entry.put("maskStrategyId", fp.maskStrategyId());
                fieldGrants.add(entry);
            }
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("shareId", share.id());
        payload.put("tenantId", share.tenantId());
        payload.put("resourceId", share.resourceId());
        payload.put("resourceInstanceKey", share.resourceInstanceKey());
        payload.put("targetType", share.targetType().name());
        payload.put("targetId", share.targetId());
        payload.put("operationIds", normalizedOperations(operationIds));
        payload.put("startTime", share.startTime());
        payload.put("expireTime", share.expireTime());
        payload.put("canReshare", share.canReshare());
        payload.put("epoch", epoch);
        payload.put("fieldGrants", fieldGrants);
        payload.put("scopeType", share.scopeType().name());
        if (share.folderPath() != null) {
            payload.put("folderPath", share.folderPath());
        }
        if (!share.collectionResourceIds().isEmpty()) {
            payload.put("collectionResourceIds", share.collectionResourceIds());
        }
        return write(payload);
    }

    public String encodeRevoked(Share share, List<Long> operationIds, long epoch) {
        Objects.requireNonNull(share, "share must not be null");
        Objects.requireNonNull(operationIds, "operationIds must not be null");
        if (epoch < MIN_EPOCH) {
            throw new IllegalArgumentException("revoke epoch must be >= 1");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("shareId", share.id());
        payload.put("tenantId", share.tenantId());
        payload.put("resourceId", share.resourceId());
        payload.put("resourceInstanceKey", share.resourceInstanceKey());
        payload.put("targetType", share.targetType().name());
        payload.put("targetId", share.targetId());
        payload.put("operationIds", normalizedOperations(operationIds));
        payload.put("epoch", epoch);
        return write(payload);
    }

    public Map<String, Object> decodeGranted(String payload) {
        Map<String, Object> decoded = decode(payload);
        requireCommonFields(decoded);
        requireInstant(decoded, "startTime");
        requireInstant(decoded, "expireTime");
        Object canReshare = requireValue(decoded, "canReshare");
        if (!(canReshare instanceof Boolean)) {
            throw new ShareAclEventFormatException("canReshare must be a boolean");
        }
        Long epoch = asLong(decoded, "epoch");
        if (epoch == null || epoch < MIN_EPOCH) {
            throw new ShareAclEventFormatException("granted event must carry a positive epoch");
        }
        validateOptionalFieldGrants(decoded);
        return decoded;
    }

    public Map<String, Object> decodeRevoked(String payload) {
        Map<String, Object> decoded = decode(payload);
        requireCommonFields(decoded);
        Long epoch = asLong(decoded, "epoch");
        if (epoch == null || epoch < MIN_EPOCH) {
            throw new ShareAclEventFormatException("epoch must be a positive integer");
        }
        return decoded;
    }

    private Map<String, Object> decode(String payload) {
        if (payload == null || payload.isBlank()) {
            throw new ShareAclEventFormatException("payload must not be blank");
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> decoded = mapper.readValue(payload, Map.class);
            return decoded;
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new ShareAclEventFormatException(
                    "share ACL event payload is invalid", exception);
        }
    }

    private void requireCommonFields(Map<String, Object> decoded) {
        for (String field : List.of(
                "shareId", "tenantId", "resourceId", "targetId", "epoch")) {
            Long value = asLong(decoded, field);
            if (value == null || value <= 0) {
                throw new ShareAclEventFormatException(field + " must be a positive integer");
            }
        }
        Object instanceKey = requireValue(decoded, "resourceInstanceKey");
        if (!(instanceKey instanceof String) || ((String) instanceKey).isBlank()) {
            throw new ShareAclEventFormatException("resourceInstanceKey must be a string");
        }
        Object targetType = requireValue(decoded, "targetType");
        if (!(targetType instanceof String type)
                || (!type.equals(ShareTargetType.USER.name())
                        && !type.equals(ShareTargetType.TEAM.name())
                        && !type.equals(ShareTargetType.ROLE.name())
                        && !type.equals(ShareTargetType.TEAM_ROLE.name()))) {
            throw new ShareAclEventFormatException("targetType must be USER, TEAM, ROLE, or TEAM_ROLE");
        }
        Object operations = requireValue(decoded, "operationIds");
        if (!(operations instanceof List<?> list) || list.isEmpty()) {
            throw new ShareAclEventFormatException("operationIds must be a non-empty list");
        }
        for (Object operation : list) {
            if (!(operation instanceof Number number) || number.longValue() <= 0
                    || number.doubleValue() % 1 != 0) {
                throw new ShareAclEventFormatException(
                        "operationIds must contain positive integers");
            }
        }
    }

    private static Object requireValue(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value == null) {
            throw new ShareAclEventFormatException(key + " is required");
        }
        return value;
    }

    private static Long asLong(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value instanceof Number number && number.doubleValue() % 1 == 0) {
            return number.longValue();
        }
        return null;
    }

    private static Instant requireInstant(Map<String, Object> payload, String key) {
        Object value = requireValue(payload, key);
        if (!(value instanceof String text)) {
            throw new ShareAclEventFormatException(key + " must be an ISO-8601 instant");
        }
        try {
            return Instant.parse(text);
        } catch (RuntimeException exception) {
            throw new ShareAclEventFormatException(key + " must be an ISO-8601 instant", exception);
        }
    }

    private static List<Long> normalizedOperations(List<Long> operationIds) {
        if (operationIds.isEmpty()) {
            throw new IllegalArgumentException("operationIds must not be empty");
        }
        List<Long> normalized = new ArrayList<>();
        for (Long operationId : operationIds) {
            if (operationId == null || operationId <= 0) {
                throw new IllegalArgumentException("operationIds must contain positive values");
            }
            normalized.add(operationId);
        }
        return List.copyOf(normalized);
    }

    @SuppressWarnings("unchecked")
    private void validateOptionalFieldGrants(Map<String, Object> decoded) {
        Object raw = decoded.get("fieldGrants");
        if (raw == null) {
            return;
        }
        if (!(raw instanceof List<?> list)) {
            throw new ShareAclEventFormatException("fieldGrants must be an array");
        }
        for (Object element : list) {
            if (!(element instanceof Map<?, ?> entry)) {
                throw new ShareAclEventFormatException("fieldGrants entries must be objects");
            }
            Long operationId = asLong((Map<String, Object>) entry, "operationId");
            if (operationId == null || operationId <= 0) {
                throw new ShareAclEventFormatException(
                        "fieldGrant operationId must be a positive integer");
            }
            Long fieldId = asLong((Map<String, Object>) entry, "fieldId");
            if (fieldId == null || fieldId <= 0) {
                throw new ShareAclEventFormatException(
                        "fieldGrant fieldId must be a positive integer");
            }
            Object readable = entry.get("readable");
            if (!(readable instanceof Boolean)) {
                throw new ShareAclEventFormatException("fieldGrant readable must be a boolean");
            }
            Object writable = entry.get("writable");
            if (!(writable instanceof Boolean)) {
                throw new ShareAclEventFormatException("fieldGrant writable must be a boolean");
            }
            Object hidden = entry.get("hidden");
            if (!(hidden instanceof Boolean)) {
                throw new ShareAclEventFormatException("fieldGrant hidden must be a boolean");
            }
            Object maskStrategyId = entry.get("maskStrategyId");
            if (maskStrategyId != null) {
                Long mid = asLong((Map<String, Object>) entry, "maskStrategyId");
                if (mid == null || mid <= 0) {
                    throw new ShareAclEventFormatException(
                            "fieldGrant maskStrategyId must be a positive integer or null");
                }
            }
        }
    }

    private String write(Map<String, Object> payload) {
        try {
            return mapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new ShareAclEventFormatException(
                    "share ACL event could not be encoded", exception);
        }
    }
}

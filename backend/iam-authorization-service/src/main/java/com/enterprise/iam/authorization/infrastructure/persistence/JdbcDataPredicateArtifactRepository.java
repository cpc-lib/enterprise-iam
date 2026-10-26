package com.enterprise.iam.authorization.infrastructure.persistence;

import com.enterprise.iam.authorization.application.port.out.DataPredicateArtifactRepository;
import com.enterprise.iam.authorization.domain.model.DataPredicateArtifact;
import com.enterprise.iam.authorization.infrastructure.persistence.mapper.AuthorizationArtifactMapper;
import com.enterprise.iam.authorization.infrastructure.persistence.row.ArtifactRow;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Persists typed data-predicate artifacts to {@code iam_data_predicate_artifact}.
 * The scope list is stored as JSON; reads re-validate through the domain
 * constructor and any malformed or expired row resolves to empty (fail closed).
 */
public final class JdbcDataPredicateArtifactRepository implements DataPredicateArtifactRepository {

    private final AuthorizationArtifactMapper mapper;
    private final ObjectMapper objectMapper;

    public JdbcDataPredicateArtifactRepository(AuthorizationArtifactMapper mapper, ObjectMapper objectMapper) {
        this.mapper = Objects.requireNonNull(mapper);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    @Override
    public void save(DataPredicateArtifact artifact) {
        Objects.requireNonNull(artifact, "artifact must not be null");
        String payload;
        try {
            payload = objectMapper.writeValueAsString(toDataScopesArray(artifact.scopes()));
        } catch (Exception e) {
            throw new IllegalStateException("failed to serialize data predicate artifact", e);
        }
        mapper.insertDataPredicateArtifact(new ArtifactRow(
                artifact.ref(),
                artifact.tenantId(),
                artifact.subjectId(),
                artifact.resourceId(),
                artifact.operationId(),
                artifact.permissionVersion(),
                artifact.decisionId(),
                payload,
                artifact.expiresAt()));
    }

    @Override
    public Optional<DataPredicateArtifact> findActiveByRef(String ref, Instant now) {
        Objects.requireNonNull(ref, "ref must not be null");
        Objects.requireNonNull(now, "now must not be null");
        ArtifactRow row = mapper.selectActiveDataPredicateArtifact(ref, now);
        if (row == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(new DataPredicateArtifact(
                    row.ref(), row.tenantId(), row.subjectId(), row.resourceId(),
                    row.operationId(), row.permissionVersion(), row.decisionId(),
                    parseScopes(row.payloadJson()), row.expiresAt()));
        } catch (Exception e) {
            // Fail closed: malformed payload means no artifact.
            return Optional.empty();
        }
    }

    /**
     * Payload is a bare dataScopes array — identical in shape to the
     * obligations key consumers already parse, so one resolver serves both.
     */
    private static List<Map<String, Object>> toDataScopesArray(List<DataPredicateArtifact.Entry> scopes) {
        List<Map<String, Object>> entries = new ArrayList<>();
        for (DataPredicateArtifact.Entry scope : scopes) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("scopeType", scope.scopeType());
            entry.put("mergeMode", scope.mergeMode());
            if (scope.scopeCode() != null) {
                entry.put("scopeCode", scope.scopeCode());
            }
            if (scope.customPolicyId() != null) {
                entry.put("customPolicyId", scope.customPolicyId());
            }
            if (!scope.teamIds().isEmpty()) {
                entry.put("teamIds", scope.teamIds());
            }
            entries.add(entry);
        }
        return entries;
    }

    private List<DataPredicateArtifact.Entry> parseScopes(String payloadJson) throws Exception {
        List<?> list = objectMapper.readValue(payloadJson, List.class);
        List<DataPredicateArtifact.Entry> entries = new ArrayList<>();
        for (Object element : list) {
            if (!(element instanceof Map<?, ?> map)) {
                throw new IllegalArgumentException("scope entry must be an object");
            }
            Object scopeCode = map.get("scopeCode");
            Object customPolicyId = map.get("customPolicyId");
            entries.add(new DataPredicateArtifact.Entry(
                    requireString(map.get("scopeType"), "scopeType"),
                    scopeCode instanceof String code ? code : null,
                    customPolicyId instanceof Number n ? n.longValue() : null,
                    requireString(map.get("mergeMode"), "mergeMode"),
                    parseTeamIds(map.get("teamIds"))));
        }
        return entries;
    }

    private static String requireString(Object value, String key) {
        if (!(value instanceof String s)) {
            throw new IllegalArgumentException(key + " must be a string");
        }
        return s;
    }

    private static List<Long> parseTeamIds(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Long> teamIds = new ArrayList<>();
        for (Object element : list) {
            if (element instanceof Number n) {
                teamIds.add(n.longValue());
            }
        }
        return teamIds;
    }
}

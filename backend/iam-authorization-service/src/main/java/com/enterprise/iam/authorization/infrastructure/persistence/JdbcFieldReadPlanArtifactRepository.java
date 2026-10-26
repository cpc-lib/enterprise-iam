package com.enterprise.iam.authorization.infrastructure.persistence;

import com.enterprise.iam.authorization.application.port.out.FieldReadPlanArtifactRepository;
import com.enterprise.iam.authorization.domain.model.FieldReadPlanArtifact;
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
 * Persists typed field-read plan artifacts to
 * {@code iam_field_read_plan_artifact}. Malformed or expired rows resolve
 * empty (fail closed).
 */
public final class JdbcFieldReadPlanArtifactRepository implements FieldReadPlanArtifactRepository {

    private final AuthorizationArtifactMapper mapper;
    private final ObjectMapper objectMapper;

    public JdbcFieldReadPlanArtifactRepository(AuthorizationArtifactMapper mapper, ObjectMapper objectMapper) {
        this.mapper = Objects.requireNonNull(mapper);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    @Override
    public void save(FieldReadPlanArtifact artifact) {
        Objects.requireNonNull(artifact, "artifact must not be null");
        String payload;
        try {
            payload = objectMapper.writeValueAsString(toEntriesArray(artifact.entries()));
        } catch (Exception e) {
            throw new IllegalStateException("failed to serialize field-read plan artifact", e);
        }
        mapper.insertFieldReadPlanArtifact(new ArtifactRow(
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
    public Optional<FieldReadPlanArtifact> findActiveByRef(String ref, Instant now) {
        Objects.requireNonNull(ref, "ref must not be null");
        Objects.requireNonNull(now, "now must not be null");
        ArtifactRow row = mapper.selectActiveFieldReadPlanArtifact(ref, now);
        if (row == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(new FieldReadPlanArtifact(
                    row.ref(), row.tenantId(), row.subjectId(), row.resourceId(),
                    row.operationId(), row.permissionVersion(), row.decisionId(),
                    parseEntries(row.payloadJson()), row.expiresAt()));
        } catch (Exception e) {
            // Fail closed: malformed payload means no artifact.
            return Optional.empty();
        }
    }

    /** Bare entries array, same shape consumers parse from field-grant obligations. */
    private static List<Map<String, Object>> toEntriesArray(List<FieldReadPlanArtifact.Entry> entries) {
        List<Map<String, Object>> payload = new ArrayList<>();
        for (FieldReadPlanArtifact.Entry entry : entries) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("fieldId", entry.fieldId());
            map.put("policy", entry.policy());
            if (entry.maskStrategyType() != null) {
                map.put("maskStrategyType", entry.maskStrategyType());
            }
            if (entry.maskConfig() != null) {
                map.put("maskConfig", entry.maskConfig());
            }
            payload.add(map);
        }
        return payload;
    }

    @SuppressWarnings("unchecked")
    private List<FieldReadPlanArtifact.Entry> parseEntries(String payloadJson) throws Exception {
        List<?> list = objectMapper.readValue(payloadJson, List.class);
        List<FieldReadPlanArtifact.Entry> entries = new ArrayList<>();
        for (Object element : list) {
            if (!(element instanceof Map<?, ?> map)) {
                throw new IllegalArgumentException("plan entry must be an object");
            }
            Object fieldId = map.get("fieldId");
            Object policy = map.get("policy");
            if (!(fieldId instanceof Number id) || !(policy instanceof String p)) {
                throw new IllegalArgumentException("fieldId/policy missing or malformed");
            }
            Object type = map.get("maskStrategyType");
            Object config = map.get("maskConfig");
            entries.add(new FieldReadPlanArtifact.Entry(
                    id.longValue(),
                    p,
                    type instanceof String t ? t : null,
                    config instanceof Map<?, ?> c ? (Map<String, Object>) c : null));
        }
        return entries;
    }
}

package com.enterprise.iam.authorization.infrastructure.persistence;

import com.enterprise.iam.authorization.application.port.out.FieldWritePlanArtifactRepository;
import com.enterprise.iam.authorization.domain.model.FieldWritePlanArtifact;
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
 * Persists typed field-write plan artifacts to
 * {@code iam_field_write_plan_artifact}. Malformed or expired rows resolve
 * empty (fail closed).
 */
public final class JdbcFieldWritePlanArtifactRepository implements FieldWritePlanArtifactRepository {

    private final AuthorizationArtifactMapper mapper;
    private final ObjectMapper objectMapper;

    public JdbcFieldWritePlanArtifactRepository(AuthorizationArtifactMapper mapper, ObjectMapper objectMapper) {
        this.mapper = Objects.requireNonNull(mapper);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    @Override
    public void save(FieldWritePlanArtifact artifact) {
        Objects.requireNonNull(artifact, "artifact must not be null");
        String payload;
        try {
            payload = objectMapper.writeValueAsString(toEntriesArray(artifact.entries()));
        } catch (Exception e) {
            throw new IllegalStateException("failed to serialize field-write plan artifact", e);
        }
        mapper.insertFieldWritePlanArtifact(new ArtifactRow(
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
    public Optional<FieldWritePlanArtifact> findActiveByRef(String ref, Instant now) {
        Objects.requireNonNull(ref, "ref must not be null");
        Objects.requireNonNull(now, "now must not be null");
        ArtifactRow row = mapper.selectActiveFieldWritePlanArtifact(ref, now);
        if (row == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(new FieldWritePlanArtifact(
                    row.ref(), row.tenantId(), row.subjectId(), row.resourceId(),
                    row.operationId(), row.permissionVersion(), row.decisionId(),
                    parseEntries(row.payloadJson()), row.expiresAt()));
        } catch (Exception e) {
            // Fail closed: malformed payload means no artifact.
            return Optional.empty();
        }
    }

    /** Bare entries array carrying only the writable decision per field. */
    private static List<Map<String, Object>> toEntriesArray(List<FieldWritePlanArtifact.Entry> entries) {
        List<Map<String, Object>> payload = new ArrayList<>();
        for (FieldWritePlanArtifact.Entry entry : entries) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("fieldId", entry.fieldId());
            map.put("writable", entry.writable());
            payload.add(map);
        }
        return payload;
    }

    private List<FieldWritePlanArtifact.Entry> parseEntries(String payloadJson) throws Exception {
        List<?> list = objectMapper.readValue(payloadJson, List.class);
        List<FieldWritePlanArtifact.Entry> entries = new ArrayList<>();
        for (Object element : list) {
            if (!(element instanceof Map<?, ?> map)) {
                throw new IllegalArgumentException("plan entry must be an object");
            }
            Object fieldId = map.get("fieldId");
            Object writable = map.get("writable");
            if (!(fieldId instanceof Number id) || !(writable instanceof Boolean w)) {
                throw new IllegalArgumentException("fieldId/writable missing or malformed");
            }
            entries.add(new FieldWritePlanArtifact.Entry(id.longValue(), w));
        }
        return entries;
    }
}

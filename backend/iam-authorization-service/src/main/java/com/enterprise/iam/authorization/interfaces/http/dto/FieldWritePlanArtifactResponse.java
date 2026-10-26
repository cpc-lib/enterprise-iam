package com.enterprise.iam.authorization.interfaces.http.dto;

import com.enterprise.iam.authorization.domain.model.FieldWritePlanArtifact;
import com.enterprise.iam.common.web.json.NumericLong;

import java.time.Instant;
import java.util.List;

/**
 * Wire view of a typed field-write plan artifact (SPEC 38 §8).
 */
public record FieldWritePlanArtifactResponse(
        String ref,
        long tenantId,
        long subjectId,
        long resourceId,
        long operationId,
        @NumericLong long permissionVersion,
        long decisionId,
        List<WriteEntry> entries,
        Instant expiresAt) {

    public record WriteEntry(long fieldId, boolean writable) {
    }

    public static FieldWritePlanArtifactResponse fromArtifact(FieldWritePlanArtifact artifact) {
        List<WriteEntry> entries = artifact.entries().stream()
                .map(e -> new WriteEntry(e.fieldId(), e.writable()))
                .toList();
        return new FieldWritePlanArtifactResponse(
                artifact.ref(),
                artifact.tenantId(),
                artifact.subjectId(),
                artifact.resourceId(),
                artifact.operationId(),
                artifact.permissionVersion(),
                artifact.decisionId(),
                entries,
                artifact.expiresAt());
    }
}

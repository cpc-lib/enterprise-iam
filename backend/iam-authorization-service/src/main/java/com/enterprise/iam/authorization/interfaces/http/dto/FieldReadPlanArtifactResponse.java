package com.enterprise.iam.authorization.interfaces.http.dto;

import com.enterprise.iam.authorization.domain.model.FieldReadPlanArtifact;
import com.enterprise.iam.common.web.json.NumericLong;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Wire view of a typed field-read plan artifact (SPEC 38 §8).
 */
public record FieldReadPlanArtifactResponse(
        String ref,
        long tenantId,
        long subjectId,
        long resourceId,
        long operationId,
        @NumericLong long permissionVersion,
        long decisionId,
        List<ReadEntry> entries,
        Instant expiresAt) {

    public record ReadEntry(
            long fieldId,
            String policy,
            String maskStrategyType,
            Map<String, Object> maskConfig) {
    }

    public static FieldReadPlanArtifactResponse fromArtifact(FieldReadPlanArtifact artifact) {
        List<ReadEntry> entries = artifact.entries().stream()
                .map(e -> new ReadEntry(
                        e.fieldId(), e.policy(), e.maskStrategyType(), e.maskConfig()))
                .toList();
        return new FieldReadPlanArtifactResponse(
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

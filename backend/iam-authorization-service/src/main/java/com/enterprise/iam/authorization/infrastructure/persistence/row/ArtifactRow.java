package com.enterprise.iam.authorization.infrastructure.persistence.row;

import java.time.Instant;

public record ArtifactRow(
        String ref,
        long tenantId,
        long subjectId,
        long resourceId,
        long operationId,
        long permissionVersion,
        long decisionId,
        String payloadJson,
        Instant expiresAt) {
}

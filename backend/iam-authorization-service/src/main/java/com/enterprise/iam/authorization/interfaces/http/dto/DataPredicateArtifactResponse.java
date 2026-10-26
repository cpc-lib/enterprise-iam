package com.enterprise.iam.authorization.interfaces.http.dto;

import com.enterprise.iam.authorization.domain.model.DataPredicateArtifact;
import com.enterprise.iam.common.web.json.NumericLong;

import java.time.Instant;
import java.util.List;

/**
 * Wire view of a typed data-predicate artifact (SPEC 38 §8). 64-bit ids
 * serialize as decimal strings via the common-web Long serializer; scopes
 * mirror the dataScopes obligation shape.
 */
public record DataPredicateArtifactResponse(
        String ref,
        long tenantId,
        long subjectId,
        long resourceId,
        long operationId,
        @NumericLong long permissionVersion,
        long decisionId,
        List<ScopeEntry> scopes,
        Instant expiresAt) {

    public record ScopeEntry(
            String scopeType,
            String scopeCode,
            Long customPolicyId,
            String mergeMode,
            List<Long> teamIds) {
    }

    public static DataPredicateArtifactResponse fromArtifact(DataPredicateArtifact artifact) {
        List<ScopeEntry> scopes = artifact.scopes().stream()
                .map(e -> new ScopeEntry(
                        e.scopeType(), e.scopeCode(), e.customPolicyId(), e.mergeMode(), e.teamIds()))
                .toList();
        return new DataPredicateArtifactResponse(
                artifact.ref(),
                artifact.tenantId(),
                artifact.subjectId(),
                artifact.resourceId(),
                artifact.operationId(),
                artifact.permissionVersion(),
                artifact.decisionId(),
                scopes,
                artifact.expiresAt());
    }
}

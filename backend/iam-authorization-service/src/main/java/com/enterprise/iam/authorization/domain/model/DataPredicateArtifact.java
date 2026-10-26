package com.enterprise.iam.authorization.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Typed server artifact behind a {@code dataPredicateRef} (SPEC 38 §8,
 * SPEC 47 §9.2 D-10). The reference binds tenant/subject/resource/operation/
 * permissionVersion and expires with the effective-policy TTL; consumers fetch
 * it through the internal data-predicate endpoint and compile the scope list
 * to SQL via the common-core DataScopeSqlCompiler. The payload is the exact
 * dataScopes obligation list the decision produced — a CUSTOM_POLICY entry is
 * preserved and fails closed downstream, never silently widened.
 */
public record DataPredicateArtifact(
        String ref,
        long tenantId,
        long subjectId,
        long resourceId,
        long operationId,
        long permissionVersion,
        long decisionId,
        List<Entry> scopes,
        Instant expiresAt) {

    public static final String REF_PREFIX = "iam:data-predicate:";

    public DataPredicateArtifact {
        Objects.requireNonNull(ref, "ref must not be null");
        if (!ref.startsWith(REF_PREFIX) || ref.length() > 256) {
            throw new IllegalArgumentException("ref must start with " + REF_PREFIX + " and fit 256 chars");
        }
        if (tenantId <= 0 || subjectId <= 0 || resourceId <= 0 || operationId <= 0 || decisionId <= 0) {
            throw new IllegalArgumentException("ids must be positive");
        }
        if (permissionVersion < 0) {
            throw new IllegalArgumentException("permissionVersion must not be negative");
        }
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        scopes = scopes == null ? List.of() : List.copyOf(scopes);
        if (scopes.size() > 100) {
            throw new IllegalArgumentException("scopes must not exceed 100 entries");
        }
    }

    /**
     * One resolved scope entry inside the artifact payload. Mirrors the
     * wire shape of the dataScopes obligation so the common-core compiler
     * can consume either source identically.
     */
    public record Entry(
            String scopeType,
            String scopeCode,
            Long customPolicyId,
            String mergeMode,
            List<Long> teamIds) {

        private static final List<String> SCOPE_TYPES = List.of(
                "ALL", "SELF", "TEAM", "TEAM_AND_CHILDREN",
                "SPECIFIED_TEAM", "SHARED", "CUSTOM_POLICY");

        public Entry {
            if (scopeType == null || !SCOPE_TYPES.contains(scopeType)) {
                throw new IllegalArgumentException("unknown scopeType: " + scopeType);
            }
            if (!"UNION".equals(mergeMode) && !"INTERSECT".equals(mergeMode)) {
                throw new IllegalArgumentException("mergeMode must be UNION or INTERSECT");
            }
            teamIds = teamIds == null ? List.of() : List.copyOf(teamIds);
        }
    }
}

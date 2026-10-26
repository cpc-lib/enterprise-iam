package com.enterprise.iam.authorization.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Typed server artifact behind a {@code fieldReadPlanRef} (SPEC 38 §8,
 * SPEC 47 §9.2 D-11). Bound to tenant/subject/resource/operation/
 * permissionVersion with the effective-policy TTL; each entry is one
 * resolved field-grant read policy. A MASK entry carries the typed mask
 * strategy when it resolved at decision time; absent type/config means
 * consumers apply their conservative generic mask (fail closed).
 */
public record FieldReadPlanArtifact(
        String ref,
        long tenantId,
        long subjectId,
        long resourceId,
        long operationId,
        long permissionVersion,
        long decisionId,
        List<Entry> entries,
        Instant expiresAt) {

    public static final String REF_PREFIX = "iam:field-read:";

    public FieldReadPlanArtifact {
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
        entries = entries == null ? List.of() : List.copyOf(entries);
        if (entries.size() > 1000) {
            throw new IllegalArgumentException("entries must not exceed 1000");
        }
    }

    public record Entry(long fieldId, String policy, String maskStrategyType, Map<String, Object> maskConfig) {

        private static final List<String> POLICIES = List.of("READ", "MASK", "HIDE");

        public Entry {
            if (fieldId <= 0) {
                throw new IllegalArgumentException("fieldId must be positive");
            }
            if (policy == null || !POLICIES.contains(policy)) {
                throw new IllegalArgumentException("unknown read policy: " + policy);
            }
            maskConfig = maskConfig == null ? null : Map.copyOf(maskConfig);
        }
    }
}

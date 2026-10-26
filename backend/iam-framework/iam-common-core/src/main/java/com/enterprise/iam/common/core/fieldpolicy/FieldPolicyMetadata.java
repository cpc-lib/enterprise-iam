package com.enterprise.iam.common.core.fieldpolicy;

import java.util.Map;
import java.util.Objects;

/**
 * Immutable field-policy metadata for a single resource type. Read keys map
 * to the resolved {@link FieldPolicy}; write keys name the fields the
 * principal is explicitly allowed to mutate. Absence from the write map is
 * fail-closed: unregistered fields are never writable.
 *
 * <p>V2.0 supports dot-notation dynamic map field paths (e.g.,
 * {@code extAttributes.salary}, {@code customFields.idCard}) as well as
 * prefix wildcards (e.g., {@code extAttributes.*}). Exact field path rules
 * take precedence over wildcard rules.
 */
public record FieldPolicyMetadata(
        String resourceType,
        Map<String, FieldPolicy> fields,
        Map<String, Boolean> writableFields,
        Map<String, MaskStrategySpec> maskStrategies) {

    public FieldPolicyMetadata {
        Objects.requireNonNull(resourceType, "resourceType must not be null");
        if (resourceType.isBlank()) {
            throw new IllegalArgumentException("resourceType must not be blank");
        }
        fields = fields == null ? Map.of() : Map.copyOf(fields);
        writableFields = writableFields == null ? Map.of() : Map.copyOf(writableFields);
        maskStrategies = maskStrategies == null ? Map.of() : Map.copyOf(maskStrategies);
    }

    /** Read-only metadata without a write plan: every field is write-denied. */
    public FieldPolicyMetadata(String resourceType, Map<String, FieldPolicy> fields) {
        this(resourceType, fields, Map.of(), Map.of());
    }

    /** Backward-compatible metadata without resolved typed mask strategies. */
    public FieldPolicyMetadata(
            String resourceType,
            Map<String, FieldPolicy> fields,
            Map<String, Boolean> writableFields) {
        this(resourceType, fields, writableFields, Map.of());
    }

    public FieldPolicy policyFor(String fieldName) {
        if (fieldName == null) {
            return FieldPolicy.HIDE;
        }
        FieldPolicy exact = fields.get(fieldName);
        if (exact != null) {
            return exact;
        }
        FieldPolicy wildcard = lookupByWildcard(fields, fieldName);
        return wildcard != null ? wildcard : FieldPolicy.HIDE;
    }

    public boolean isWritable(String fieldName) {
        if (fieldName == null) {
            return false;
        }
        Boolean exact = writableFields.get(fieldName);
        if (exact != null) {
            return exact;
        }
        Boolean wildcard = lookupByWildcard(writableFields, fieldName);
        return Boolean.TRUE.equals(wildcard);
    }

    /** Typed mask strategy for the field, or null when none was resolved. */
    public MaskStrategySpec maskStrategyFor(String fieldName) {
        if (fieldName == null) {
            return null;
        }
        MaskStrategySpec exact = maskStrategies.get(fieldName);
        if (exact != null) {
            return exact;
        }
        return lookupByWildcard(maskStrategies, fieldName);
    }

    public boolean hasNestedReadRules(String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return false;
        }
        String dotPrefix = prefix + ".";
        for (String key : fields.keySet()) {
            if (key.startsWith(dotPrefix)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasNestedWriteRules(String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return false;
        }
        String dotPrefix = prefix + ".";
        for (String key : writableFields.keySet()) {
            if (key.startsWith(dotPrefix)) {
                return true;
            }
        }
        return false;
    }

    private static <T> T lookupByWildcard(Map<String, T> map, String fieldPath) {
        int dotIndex = fieldPath.lastIndexOf('.');
        while (dotIndex > 0) {
            String wildcardKey = fieldPath.substring(0, dotIndex) + ".*";
            T matched = map.get(wildcardKey);
            if (matched != null) {
                return matched;
            }
            dotIndex = fieldPath.lastIndexOf('.', dotIndex - 1);
        }
        return map.get("*");
    }
}

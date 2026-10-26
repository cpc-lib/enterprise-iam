package com.enterprise.iam.common.core.fieldpolicy;

import com.enterprise.iam.common.core.masking.TypedMasker;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Applies field policies to a response map and validates write payloads.
 * Framework-free: operates on generic maps only.
 *
 * <p>V2.0 supports both top-level fields and dynamic nested {@code Map<String, Object>}
 * paths addressed via dot notation (e.g., {@code extAttributes.salary},
 * {@code customFields.idCard}, or {@code extAttributes.*}).
 */
public final class FieldPolicyEnforcer {

    private FieldPolicyEnforcer() {
    }

    /**
     * Filters and masks a response map according to the supplied metadata.
     * HIDE fields are removed; MASK fields are replaced with a masked value.
     * Nested maps are recursively filtered when dot-notation or wildcard
     * rules exist for their path prefix.
     */
    public static Map<String, Object> filterResponse(
            Map<String, Object> source, FieldPolicyMetadata metadata) {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(metadata, "metadata must not be null");
        return filterMap(source, "", metadata);
    }

    /**
     * Validates that a write payload does not contain fields the principal is
     * not allowed to mutate (including nested dynamic map paths). Throws
     * {@link IllegalArgumentException} if any non-writable field is present.
     */
    public static void guardWrite(Map<String, Object> payload, FieldPolicyMetadata metadata) {
        Objects.requireNonNull(payload, "payload must not be null");
        Objects.requireNonNull(metadata, "metadata must not be null");
        guardWriteMap(payload, "", metadata);
    }

    private static Map<String, Object> filterMap(
            Map<String, Object> currentMap,
            String prefix,
            FieldPolicyMetadata metadata) {
        Map<String, Object> result = new LinkedHashMap<>();
        currentMap.forEach((field, value) -> {
            String fieldPath = prefix.isEmpty() ? field : prefix + "." + field;
            if (value instanceof Map<?, ?> nestedRaw) {
                FieldPolicy directPolicy = metadata.fields().get(fieldPath);
                boolean hasNestedRules = metadata.hasNestedReadRules(fieldPath);
                if (directPolicy == FieldPolicy.HIDE && !hasNestedRules) {
                    return;
                }
                if (hasNestedRules || directPolicy == null) {
                    Map<String, Object> filteredNested = filterMap(
                            castToStringObjectMap(nestedRaw), fieldPath, metadata);
                    if (!filteredNested.isEmpty()
                            || directPolicy == FieldPolicy.READ
                            || directPolicy == FieldPolicy.WRITE_DENIED) {
                        result.put(field, filteredNested);
                    }
                    return;
                }
            }
            FieldPolicy policy = metadata.policyFor(fieldPath);
            switch (policy) {
                case READ -> result.put(field, value);
                case MASK -> result.put(field, mask(fieldPath, value, metadata));
                case HIDE -> { /* omitted */ }
                case WRITE_DENIED -> result.put(field, value);
            }
        });
        return result;
    }

    private static void guardWriteMap(
            Map<String, Object> currentPayload,
            String prefix,
            FieldPolicyMetadata metadata) {
        currentPayload.forEach((field, value) -> {
            String fieldPath = prefix.isEmpty() ? field : prefix + "." + field;
            if (value instanceof Map<?, ?> nestedRaw) {
                boolean hasNestedWriteRules = metadata.hasNestedWriteRules(fieldPath);
                if (hasNestedWriteRules) {
                    if (nestedRaw.isEmpty()) {
                        if (!metadata.isWritable(fieldPath)) {
                            throw new IllegalArgumentException("field not writable: " + fieldPath);
                        }
                    } else {
                        guardWriteMap(castToStringObjectMap(nestedRaw), fieldPath, metadata);
                    }
                    return;
                }
                if (!metadata.isWritable(fieldPath)) {
                    if (!nestedRaw.isEmpty()) {
                        guardWriteMap(castToStringObjectMap(nestedRaw), fieldPath, metadata);
                        return;
                    }
                    throw new IllegalArgumentException("field not writable: " + fieldPath);
                }
                return;
            }
            if (!metadata.isWritable(fieldPath)) {
                throw new IllegalArgumentException("field not writable: " + fieldPath);
            }
        });
    }

    private static Map<String, Object> castToStringObjectMap(Map<?, ?> raw) {
        Map<String, Object> copy = new LinkedHashMap<>();
        raw.forEach((k, v) -> copy.put(String.valueOf(k), v));
        return copy;
    }

    private static Object mask(String fieldPath, Object value, FieldPolicyMetadata metadata) {
        if (value == null) {
            return null;
        }
        MaskStrategySpec spec = metadata.maskStrategyFor(fieldPath);
        if (spec != null) {
            return TypedMasker.mask(spec.type(), spec.config(), value.toString());
        }
        // Fallback for producers that carry no typed strategy: conservative
        // generic mask that reveals at most the first and last character.
        String text = value.toString();
        if (text.length() <= 2) {
            return "**";
        }
        return text.charAt(0) + "***" + text.charAt(text.length() - 1);
    }
}

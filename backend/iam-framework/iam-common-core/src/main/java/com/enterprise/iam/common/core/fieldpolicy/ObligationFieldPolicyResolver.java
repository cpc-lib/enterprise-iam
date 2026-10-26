package com.enterprise.iam.common.core.fieldpolicy;

import com.enterprise.iam.common.core.masking.MaskStrategyType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Resolves field-policy metadata from authorization-check obligations.
 * Consuming services call this with the obligations map from an
 * {@code AuthorizationCheckResponse}, then pass the result to
 * {@link FieldPolicyEnforcer#filterResponse} for read shaping or
 * {@link FieldPolicyEnforcer#guardWrite} for write validation.
 */
public final class ObligationFieldPolicyResolver {

    private ObligationFieldPolicyResolver() {
    }

    /**
     * Extracts a {@link FieldPolicyMetadata} from the obligations of an
     * authorization check response. When the obligations contain a
     * {@code fieldGrants} array, each entry's {@code fieldId}, {@code policy}
     * and {@code writable} flag are mapped. Unknown policies and missing
     * arrays yield an empty metadata (all fields default to HIDE and no
     * field is writable — both directions fail closed).
     */
    @SuppressWarnings("unchecked")
    public static FieldPolicyMetadata fromObligations(
            String resourceType, Map<String, Object> obligations) {
        Objects.requireNonNull(resourceType, "resourceType must not be null");
        Objects.requireNonNull(obligations, "obligations must not be null");
        Object raw = obligations.get("fieldGrants");
        if (raw == null || !(raw instanceof List<?> list)) {
            return new FieldPolicyMetadata(resourceType, Map.of(), Map.of());
        }
        Map<String, FieldPolicy> fields = new LinkedHashMap<>();
        Map<String, Boolean> writableFields = new LinkedHashMap<>();
        Map<String, MaskStrategySpec> maskStrategies = new LinkedHashMap<>();
        for (Object element : list) {
            if (!(element instanceof Map<?, ?> entry)) {
                continue;
            }
            Object fieldIdObj = ((Map<String, Object>) entry).get("fieldId");
            if (fieldIdObj == null) {
                continue;
            }
            String fieldName = fieldIdObj.toString();
            Object policyObj = ((Map<String, Object>) entry).get("policy");
            fields.put(fieldName, resolvePolicy(policyObj));
            // Absent writable flag means an older producer: fail closed on
            // the write direction rather than defaulting to mutable.
            writableFields.put(fieldName, Boolean.TRUE.equals(
                    ((Map<String, Object>) entry).get("writable")));
            MaskStrategySpec spec = resolveMaskStrategy((Map<String, Object>) entry);
            if (spec != null) {
                maskStrategies.put(fieldName, spec);
            }
        }
        return new FieldPolicyMetadata(resourceType, fields, writableFields, maskStrategies);
    }

    /**
     * Resolves the typed mask strategy keys. Unknown types are dropped
     * entirely so the enforcer falls back to a conservative generic mask
     * (fail closed) instead of guessing at a producer-specific behavior.
     */
    private static MaskStrategySpec resolveMaskStrategy(Map<String, Object> entry) {
        Object typeObj = entry.get("maskStrategyType");
        if (!(typeObj instanceof String typeName)) {
            return null;
        }
        MaskStrategyType type;
        try {
            type = MaskStrategyType.valueOf(typeName);
        } catch (IllegalArgumentException e) {
            return null;
        }
        Object configObj = entry.get("maskConfig");
        Map<String, Object> config = configObj instanceof Map<?, ?> map
                ? castConfig(map) : Map.of();
        return new MaskStrategySpec(type, config);
    }

    private static Map<String, Object> castConfig(Map<?, ?> map) {
        Map<String, Object> config = new LinkedHashMap<>();
        map.forEach((k, v) -> config.put(String.valueOf(k), v));
        return config;
    }

    private static FieldPolicy resolvePolicy(Object policyObj) {
        if (policyObj instanceof String s) {
            return switch (s) {
                case "READ" -> FieldPolicy.READ;
                case "MASK" -> FieldPolicy.MASK;
                case "HIDE" -> FieldPolicy.HIDE;
                default -> FieldPolicy.HIDE;
            };
        }
        return FieldPolicy.HIDE;
    }
}

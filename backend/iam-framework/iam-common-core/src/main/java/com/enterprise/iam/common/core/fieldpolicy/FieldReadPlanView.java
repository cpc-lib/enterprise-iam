package com.enterprise.iam.common.core.fieldpolicy;

import com.enterprise.iam.common.core.masking.MaskStrategyType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Consumer-side typed view of the field-read plan artifact returned by
 * {@code GET /internal/v1/authorization/field-read-plans/{ref}} (SPEC 38 §8,
 * SPEC 47 §9.2 D-11). Parsing is fail-closed: any malformed field yields
 * empty. Entries convert directly into the maps of {@link FieldPolicyMetadata}.
 */
public record FieldReadPlanView(
        String ref,
        long tenantId,
        long subjectId,
        long resourceId,
        long operationId,
        long permissionVersion,
        Instant expiresAt,
        List<Entry> entries) {

    public FieldReadPlanView {
        Objects.requireNonNull(ref, "ref must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        entries = entries == null ? List.of() : List.copyOf(entries);
    }

    public record Entry(long fieldId, String policy, MaskStrategyType maskStrategy, Map<String, Object> maskConfig) {
    }

    public boolean bindsTo(long tenantId, long subjectId, long resourceId,
                           long operationId, long permissionVersion) {
        return this.tenantId == tenantId
                && this.subjectId == subjectId
                && this.resourceId == resourceId
                && this.operationId == operationId
                && this.permissionVersion == permissionVersion;
    }

    /** Field key (fieldId as string) → READ/MASK/HIDE policy, metadata shape. */
    public Map<String, FieldPolicy> policyFields() {
        Map<String, FieldPolicy> fields = new LinkedHashMap<>();
        for (Entry entry : entries) {
            fields.put(Long.toString(entry.fieldId()), switch (entry.policy()) {
                case "READ" -> FieldPolicy.READ;
                case "MASK" -> FieldPolicy.MASK;
                default -> FieldPolicy.HIDE;
            });
        }
        return fields;
    }

    /** Field key → resolved typed mask strategy; entries without a type are omitted. */
    public Map<String, MaskStrategySpec> maskStrategyFields() {
        Map<String, MaskStrategySpec> masks = new LinkedHashMap<>();
        for (Entry entry : entries) {
            if (entry.maskStrategy() != null) {
                masks.put(Long.toString(entry.fieldId()),
                        new MaskStrategySpec(entry.maskStrategy(),
                                entry.maskConfig() == null ? Map.of() : entry.maskConfig()));
            }
        }
        return masks;
    }

    public static Optional<FieldReadPlanView> fromArtifact(Map<String, Object> data) {
        Objects.requireNonNull(data, "data must not be null");
        try {
            Object ref = data.get("ref");
            Object expiresAt = data.get("expiresAt");
            if (!(ref instanceof String r) || !(expiresAt instanceof String exp)) {
                return Optional.empty();
            }
            Long tenantId = parseId(data.get("tenantId"));
            Long subjectId = parseId(data.get("subjectId"));
            Long resourceId = parseId(data.get("resourceId"));
            Long operationId = parseId(data.get("operationId"));
            Long permissionVersion = parseId(data.get("permissionVersion"));
            if (tenantId == null || subjectId == null || resourceId == null
                    || operationId == null || permissionVersion == null) {
                return Optional.empty();
            }
            List<Entry> entries = new ArrayList<>();
            Object rawEntries = data.get("entries");
            if (rawEntries instanceof List<?> list) {
                for (Object element : list) {
                    if (!(element instanceof Map<?, ?> map)) {
                        return Optional.empty();
                    }
                    Long fieldId = parseId(map.get("fieldId"));
                    Object policy = map.get("policy");
                    if (fieldId == null || !(policy instanceof String p)
                            || !(p.equals("READ") || p.equals("MASK") || p.equals("HIDE"))) {
                        return Optional.empty();
                    }
                    MaskStrategyType type = null;
                    Object rawType = map.get("maskStrategyType");
                    if (rawType instanceof String typeName) {
                        try {
                            type = MaskStrategyType.valueOf(typeName);
                        } catch (IllegalArgumentException e) {
                            return Optional.empty();
                        }
                    }
                    Object rawConfig = map.get("maskConfig");
                    if (rawConfig != null && !(rawConfig instanceof Map<?, ?>)) {
                        return Optional.empty();
                    }
                    entries.add(new Entry(fieldId, p, type, toStringMap(rawConfig)));
                }
            } else if (rawEntries != null) {
                return Optional.empty();
            }
            return Optional.of(new FieldReadPlanView(
                    r, tenantId, subjectId, resourceId, operationId,
                    permissionVersion, Instant.parse(exp), entries));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toStringMap(Object rawConfig) {
        if (!(rawConfig instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> config = new LinkedHashMap<>();
        map.forEach((k, v) -> config.put(String.valueOf(k), v));
        return config;
    }

    private static Long parseId(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        if (value instanceof String s) {
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}

package com.enterprise.iam.common.core.fieldpolicy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Consumer-side typed view of the field-write plan artifact returned by
 * {@code GET /internal/v1/authorization/field-write-plans/{ref}} (SPEC 38 §8,
 * SPEC 47 §9.2 D-11). Parsing is fail-closed: any malformed entry yields
 * empty. Only fields explicitly marked writable pass guardWrite.
 */
public record FieldWritePlanView(
        String ref,
        long tenantId,
        long subjectId,
        long resourceId,
        long operationId,
        long permissionVersion,
        Instant expiresAt,
        List<Entry> entries) {

    public FieldWritePlanView {
        Objects.requireNonNull(ref, "ref must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        entries = entries == null ? List.of() : List.copyOf(entries);
    }

    public record Entry(long fieldId, boolean writable) {
    }

    public boolean bindsTo(long tenantId, long subjectId, long resourceId,
                           long operationId, long permissionVersion) {
        return this.tenantId == tenantId
                && this.subjectId == subjectId
                && this.resourceId == resourceId
                && this.operationId == operationId
                && this.permissionVersion == permissionVersion;
    }

    /** Field key → writable flag, metadata shape. */
    public Map<String, Boolean> writableFields() {
        Map<String, Boolean> writable = new LinkedHashMap<>();
        for (Entry entry : entries) {
            writable.put(Long.toString(entry.fieldId()), entry.writable());
        }
        return writable;
    }

    public static Optional<FieldWritePlanView> fromArtifact(Map<String, Object> data) {
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
                    Object writable = map.get("writable");
                    if (fieldId == null || !(writable instanceof Boolean w)) {
                        return Optional.empty();
                    }
                    entries.add(new Entry(fieldId, w));
                }
            } else if (rawEntries != null) {
                return Optional.empty();
            }
            return Optional.of(new FieldWritePlanView(
                    r, tenantId, subjectId, resourceId, operationId,
                    permissionVersion, Instant.parse(exp), entries));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
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

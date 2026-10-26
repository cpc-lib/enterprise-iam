package com.enterprise.iam.common.core.datascope;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Consumer-side typed view of the data-predicate artifact returned by
 * {@code GET /internal/v1/authorization/data-predicates/{ref}} (SPEC 38 §8,
 * SPEC 47 §9.2 D-10). Id fields accept both decimal strings and JSON numbers
 * (the wire contract serializes 64-bit ids as strings). Parsing is
 * fail-closed: any malformed field yields empty.
 */
public record DataPredicateView(
        String ref,
        long tenantId,
        long subjectId,
        long resourceId,
        long operationId,
        long permissionVersion,
        Instant expiresAt,
        List<DataScopeObligation> scopes) {

    public DataPredicateView {
        Objects.requireNonNull(ref, "ref must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        scopes = scopes == null ? List.of() : List.copyOf(scopes);
    }

    public static Optional<DataPredicateView> fromArtifact(Map<String, Object> data) {
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
            Object rawScopes = data.get("scopes");
            List<DataScopeObligation> scopes = new ArrayList<>();
            if (rawScopes instanceof List<?> list) {
                for (Object element : list) {
                    Optional<DataScopeObligation> scope = parseScope(element);
                    if (scope.isEmpty()) {
                        return Optional.empty();
                    }
                    scopes.add(scope.get());
                }
            } else if (rawScopes != null) {
                return Optional.empty();
            }
            return Optional.of(new DataPredicateView(
                    r, tenantId, subjectId, resourceId, operationId,
                    permissionVersion, Instant.parse(exp), scopes));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private static Optional<DataScopeObligation> parseScope(Object element) {
        if (!(element instanceof Map<?, ?> map)) {
            return Optional.empty();
        }
        Object scopeType = map.get("scopeType");
        Object mergeMode = map.get("mergeMode");
        if (!(scopeType instanceof String s) || !(mergeMode instanceof String m)) {
            return Optional.empty();
        }
        Object scopeCode = map.get("scopeCode");
        List<Long> teamIds = new ArrayList<>();
        Object rawTeamIds = map.get("teamIds");
        if (rawTeamIds instanceof List<?> list) {
            for (Object id : list) {
                Long parsed = parseId(id);
                if (parsed == null) {
                    return Optional.empty();
                }
                teamIds.add(parsed);
            }
        } else if (rawTeamIds != null) {
            return Optional.empty();
        }
        try {
            return Optional.of(new DataScopeObligation(
                    s,
                    scopeCode instanceof String code ? code : null,
                    parseId(map.get("customPolicyId")),
                    m,
                    teamIds));
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

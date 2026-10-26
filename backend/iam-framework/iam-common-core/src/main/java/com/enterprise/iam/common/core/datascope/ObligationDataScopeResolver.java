package com.enterprise.iam.common.core.datascope;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Resolves a {@link DataScopeMetadata} from the {@code dataScopes} array in
 * authorization-check obligations. Unknown scope types are preserved (the
 * enforcer fails closed on them); a missing array yields empty metadata,
 * which denies all rows.
 */
public final class ObligationDataScopeResolver {

    private static final List<String> KNOWN_SCOPE_TYPES = List.of(
            "ALL", "SELF", "TEAM", "TEAM_AND_CHILDREN",
            "SPECIFIED_TEAM", "SHARED", "CUSTOM_POLICY");

    private ObligationDataScopeResolver() {
    }

    @SuppressWarnings("unchecked")
    public static DataScopeMetadata fromObligations(
            String resourceType, Map<String, Object> obligations) {
        Objects.requireNonNull(resourceType, "resourceType must not be null");
        Objects.requireNonNull(obligations, "obligations must not be null");
        Object raw = obligations.get("dataScopes");
        if (!(raw instanceof List<?> list)) {
            return new DataScopeMetadata(resourceType, List.of());
        }
        List<DataScopeObligation> scopes = new ArrayList<>();
        for (Object element : list) {
            if (!(element instanceof Map<?, ?> entry)) {
                continue;
            }
            Map<String, Object> map = (Map<String, Object>) entry;
            Object scopeType = map.get("scopeType");
            Object mergeMode = map.get("mergeMode");
            if (!(scopeType instanceof String s)
                    || !KNOWN_SCOPE_TYPES.contains(s)
                    || !(mergeMode instanceof String m)
                    || (!"UNION".equals(m) && !"INTERSECT".equals(m))) {
                continue;
            }
            Object scopeCode = map.get("scopeCode");
            Object customPolicyId = map.get("customPolicyId");
            scopes.add(new DataScopeObligation(
                    s,
                    scopeCode instanceof String code ? code : null,
                    customPolicyId instanceof Number n ? n.longValue() : null,
                    m,
                    parseTeamIds(map.get("teamIds"))));
        }
        return new DataScopeMetadata(resourceType, scopes);
    }

    @SuppressWarnings("unchecked")
    private static List<Long> parseTeamIds(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Long> teamIds = new ArrayList<>();
        for (Object element : list) {
            if (element instanceof Number n) {
                teamIds.add(n.longValue());
            }
        }
        return teamIds;
    }
}

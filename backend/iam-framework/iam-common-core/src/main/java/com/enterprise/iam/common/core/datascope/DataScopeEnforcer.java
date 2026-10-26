package com.enterprise.iam.common.core.datascope;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Row-level enforcement of dataScopes obligations. Rows are generic maps
 * carrying {@code ownerUserId} (Number), {@code ownerTeamId} (Number) and
 * {@code instanceKey} (String); only the keys required by each scope type
 * are read.
 *
 * <p>Multiple scope entries fold in obligation order: UNION takes the union,
 * INTERSECT takes the intersection. CUSTOM_POLICY has no generic predicate
 * and therefore fails closed (the typed dataPredicateRef artifact from
 * SPEC 38 §8 is the V1.1+ enforcement path). Empty metadata denies all rows.
 */
public final class DataScopeEnforcer {

    public static final String OWNER_USER_ID = "ownerUserId";
    public static final String OWNER_TEAM_ID = "ownerTeamId";
    public static final String INSTANCE_KEY = "instanceKey";

    private DataScopeEnforcer() {
    }

    public record Principal(long userId, Set<Long> teamIds, Set<String> sharedInstanceKeys) {

        public Principal {
            teamIds = teamIds == null ? Set.of() : Set.copyOf(teamIds);
            sharedInstanceKeys = sharedInstanceKeys == null
                    ? Set.of() : Set.copyOf(sharedInstanceKeys);
        }
    }

    public static boolean allows(
            Map<String, Object> row, DataScopeMetadata metadata, Principal principal) {
        Objects.requireNonNull(row, "row must not be null");
        Objects.requireNonNull(metadata, "metadata must not be null");
        Objects.requireNonNull(principal, "principal must not be null");
        Boolean allowed = null;
        for (DataScopeObligation scope : metadata.scopes()) {
            boolean match = matches(row, scope, principal);
            if (allowed == null) {
                allowed = match;
            } else if ("UNION".equals(scope.mergeMode())) {
                allowed = allowed || match;
            } else {
                allowed = allowed && match;
            }
        }
        return Boolean.TRUE.equals(allowed);
    }

    public static List<Map<String, Object>> filterRows(
            List<Map<String, Object>> rows, DataScopeMetadata metadata, Principal principal) {
        Objects.requireNonNull(rows, "rows must not be null");
        return rows.stream()
                .filter(row -> allows(row, metadata, principal))
                .toList();
    }

    private static boolean matches(
            Map<String, Object> row, DataScopeObligation scope, Principal principal) {
        return switch (scope.scopeType()) {
            case "ALL" -> true;
            case "SELF" -> longValue(row.get(OWNER_USER_ID)) == principal.userId();
            case "TEAM" -> principal.teamIds().contains(longValue(row.get(OWNER_TEAM_ID)));
            case "TEAM_AND_CHILDREN", "SPECIFIED_TEAM" ->
                    scope.teamIds().contains(longValue(row.get(OWNER_TEAM_ID)));
            case "SHARED" -> row.get(INSTANCE_KEY) instanceof String key
                    && principal.sharedInstanceKeys().contains(key);
            // CUSTOM_POLICY and anything future: no generic predicate → deny.
            default -> false;
        };
    }

    private static Long longValue(Object value) {
        return value instanceof Number n ? n.longValue() : null;
    }

    /** Convenience for callers building a principal from id lists. */
    public static Set<Long> teamSet(long... teamIds) {
        Set<Long> teams = new LinkedHashSet<>();
        for (long teamId : teamIds) {
            teams.add(teamId);
        }
        return Set.copyOf(teams);
    }
}

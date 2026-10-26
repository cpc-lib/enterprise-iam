package com.enterprise.iam.common.core.datascope;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Compiles a typed {@link DataPredicateView} artifact into a parameterized
 * SQL WHERE fragment for SELECT / COUNT / export queries (SPEC 38 §8/§9,
 * SPEC 26 §65 list-detail-count parity). One compiled fragment serves all
 * three query shapes, so row visibility cannot diverge between them.
 *
 * <p>Semantics mirror {@link DataScopeEnforcer} exactly: scopes fold in
 * obligation order, UNION takes the union, INTERSECT the intersection;
 * CUSTOM_POLICY and unknown scope types fail closed; empty runtime sets
 * (principal teams, shared keys, static team lists) fail closed for that
 * branch. Binding or expiry mismatch fails the whole predicate closed.
 */
public final class DataScopeSqlCompiler {

    private static final String DENY_ALL = "1=0";
    private static final String ALLOW_ALL = "1=1";

    public static final String PARAM_USER_ID = "dpUserId";
    public static final String PARAM_PRINCIPAL_TEAMS = "dpPrincipalTeams";
    public static final String PARAM_SHARED_KEYS = "dpSharedKeys";
    public static final String PARAM_SCOPE_TEAMS_PREFIX = "dpScopeTeams";

    private DataScopeSqlCompiler() {
    }

    /** Compiled WHERE fragment plus its named parameters. */
    public record DataScopeSql(String whereClause, Map<String, Object> params) {

        public DataScopeSql {
            Objects.requireNonNull(whereClause, "whereClause must not be null");
            params = params == null ? Map.of() : Map.copyOf(params);
        }

        /** Fail-closed predicate matching no rows. */
        public static DataScopeSql denyAll() {
            return new DataScopeSql(DENY_ALL, Map.of());
        }
    }

    /** Expected decision context the artifact must be bound to. */
    public record Binding(long tenantId, long subjectId, long resourceId,
                          long operationId, long permissionVersion) {
    }

    public static DataScopeSql compile(
            DataPredicateView artifact,
            Binding expected,
            DataScopeColumnMapping columns,
            DataScopeEnforcer.Principal principal,
            Instant now) {
        Objects.requireNonNull(artifact, "artifact must not be null");
        Objects.requireNonNull(expected, "expected must not be null");
        Objects.requireNonNull(columns, "columns must not be null");
        Objects.requireNonNull(principal, "principal must not be null");
        Objects.requireNonNull(now, "now must not be null");

        if (!isBoundTo(artifact, expected) || !now.isBefore(artifact.expiresAt())) {
            return DataScopeSql.denyAll();
        }
        if (artifact.scopes().isEmpty()) {
            return DataScopeSql.denyAll();
        }

        Map<String, Object> params = new LinkedHashMap<>();
        String combined = null;
        int scopeIndex = 0;
        for (DataScopeObligation scope : artifact.scopes()) {
            String fragment = fragmentFor(scope, columns, principal, params, scopeIndex++);
            if (combined == null) {
                combined = fragment;
            } else if ("UNION".equals(scope.mergeMode())) {
                combined = "(" + combined + " OR " + fragment + ")";
            } else {
                combined = "(" + combined + " AND " + fragment + ")";
            }
        }
        return new DataScopeSql(combined == null ? DENY_ALL : combined, params);
    }

    private static boolean isBoundTo(DataPredicateView artifact, Binding expected) {
        return artifact.tenantId() == expected.tenantId()
                && artifact.subjectId() == expected.subjectId()
                && artifact.resourceId() == expected.resourceId()
                && artifact.operationId() == expected.operationId()
                && artifact.permissionVersion() == expected.permissionVersion();
    }

    private static String fragmentFor(
            DataScopeObligation scope,
            DataScopeColumnMapping columns,
            DataScopeEnforcer.Principal principal,
            Map<String, Object> params,
            int scopeIndex) {
        return switch (scope.scopeType()) {
            case "ALL" -> ALLOW_ALL;
            case "SELF" -> {
                params.put(PARAM_USER_ID, principal.userId());
                yield columns.ownerUserColumn() + " = :" + PARAM_USER_ID;
            }
            case "TEAM" -> {
                if (principal.teamIds().isEmpty()) {
                    yield DENY_ALL;
                }
                params.put(PARAM_PRINCIPAL_TEAMS, principal.teamIds().stream().sorted().toList());
                yield columns.ownerTeamColumn() + " IN (:" + PARAM_PRINCIPAL_TEAMS + ")";
            }
            case "TEAM_AND_CHILDREN", "SPECIFIED_TEAM" -> {
                if (scope.teamIds().isEmpty()) {
                    yield DENY_ALL;
                }
                String param = PARAM_SCOPE_TEAMS_PREFIX + scopeIndex;
                params.put(param, scope.teamIds().stream().sorted().toList());
                yield columns.ownerTeamColumn() + " IN (:" + param + ")";
            }
            case "SHARED" -> {
                if (principal.sharedInstanceKeys().isEmpty()) {
                    yield DENY_ALL;
                }
                params.put(PARAM_SHARED_KEYS, principal.sharedInstanceKeys().stream().sorted().toList());
                yield columns.instanceKeyColumn() + " IN (:" + PARAM_SHARED_KEYS + ")";
            }
            // CUSTOM_POLICY and anything future: no generic predicate → deny.
            default -> DENY_ALL;
        };
    }

    /** Convenience: compile from the raw artifact payload map of the GET endpoint. */
    public static DataScopeSql compile(
            Map<String, Object> artifactData,
            Binding expected,
            DataScopeColumnMapping columns,
            DataScopeEnforcer.Principal principal,
            Instant now) {
        Objects.requireNonNull(artifactData, "artifactData must not be null");
        return DataPredicateView.fromArtifact(artifactData)
                .map(view -> compile(view, expected, columns, principal, now))
                .orElseGet(DataScopeSql::denyAll);
    }
}

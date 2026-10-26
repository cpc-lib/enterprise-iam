package com.enterprise.iam.authorization.domain.model;

import java.util.List;

/**
 * A data scope bound to a role-permission grant, read at decision time.
 * teamIds carries concrete teams for SPECIFIED_TEAM and is empty for the
 * other scope types. mergeMode is UNION or INTERSECT across multiple grants.
 */
public record ResolvedDataScope(
        long rolePermissionId,
        long dataScopeId,
        String scopeCode,
        String scopeType,
        Long customPolicyId,
        String mergeMode,
        List<Long> teamIds) {

    private static final List<String> SCOPE_TYPES = List.of(
            "ALL", "SELF", "TEAM", "TEAM_AND_CHILDREN",
            "SPECIFIED_TEAM", "SHARED", "CUSTOM_POLICY");

    public ResolvedDataScope {
        if (rolePermissionId <= 0 || dataScopeId <= 0) {
            throw new IllegalArgumentException("ids must be positive");
        }
        if (scopeType == null || !SCOPE_TYPES.contains(scopeType)) {
            throw new IllegalArgumentException("unknown scopeType: " + scopeType);
        }
        if (!"UNION".equals(mergeMode) && !"INTERSECT".equals(mergeMode)) {
            throw new IllegalArgumentException("mergeMode must be UNION or INTERSECT");
        }
        teamIds = teamIds == null ? List.of() : List.copyOf(teamIds);
    }
}

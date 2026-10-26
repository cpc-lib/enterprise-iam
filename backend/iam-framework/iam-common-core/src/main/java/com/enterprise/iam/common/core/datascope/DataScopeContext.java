package com.enterprise.iam.common.core.datascope;

import java.util.Objects;
import java.util.Set;

/**
 * Immutable, framework-free data-scope context resolved for the current
 * principal. Carries only the identifiers required by SQL rewriting.
 */
public record DataScopeContext(
        DataScope scope,
        long tenantId,
        long userId,
        Set<Long> teamIds) {

    public DataScopeContext {
        Objects.requireNonNull(scope, "scope must not be null");
        if (tenantId <= 0) {
            throw new IllegalArgumentException("tenantId must be positive");
        }
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        teamIds = teamIds == null ? Set.of() : Set.copyOf(teamIds);
    }

    public static DataScopeContext all(long tenantId, long userId) {
        return new DataScopeContext(DataScope.ALL, tenantId, userId, Set.of());
    }

    public static DataScopeContext self(long tenantId, long userId) {
        return new DataScopeContext(DataScope.SELF, tenantId, userId, Set.of());
    }
}

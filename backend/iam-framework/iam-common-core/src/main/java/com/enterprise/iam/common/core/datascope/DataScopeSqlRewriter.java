package com.enterprise.iam.common.core.datascope;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Appends a WHERE predicate fragment that enforces the resolved data scope.
 * Framework-free: produces SQL text only, never touches connections.
 */
public final class DataScopeSqlRewriter {

    private DataScopeSqlRewriter() {
    }

    /**
     * @param tableAlias optional table alias used for tenant_id/user_id/team_id columns
     * @return a SQL fragment starting with " AND " that can be appended to an
     *         existing WHERE clause. Empty string for {@link DataScope#ALL}.
     */
    public static String appendPredicate(DataScopeContext context, String tableAlias) {
        Objects.requireNonNull(context, "context must not be null");
        String prefix = (tableAlias == null || tableAlias.isBlank()) ? "" : tableAlias + ".";
        return switch (context.scope()) {
            case ALL -> "";
            case TENANT -> " AND " + prefix + "tenant_id = " + context.tenantId();
            case TEAM, TEAM_AND_DESCENDANTS -> teamPredicate(context, prefix);
            case SELF -> " AND " + prefix + "tenant_id = " + context.tenantId()
                    + " AND " + prefix + "created_by = " + context.userId();
        };
    }

    private static String teamPredicate(DataScopeContext context, String prefix) {
        Set<Long> teamIds = context.teamIds();
        if (teamIds.isEmpty()) {
            return " AND 1 = 0";
        }
        String list = teamIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        return " AND " + prefix + "tenant_id = " + context.tenantId()
                + " AND " + prefix + "team_id IN (" + list + ")";
    }
}

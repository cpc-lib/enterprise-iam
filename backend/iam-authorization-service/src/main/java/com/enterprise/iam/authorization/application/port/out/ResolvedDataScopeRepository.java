package com.enterprise.iam.authorization.application.port.out;

import com.enterprise.iam.authorization.domain.model.ResolvedDataScope;

import java.util.List;

/**
 * Read-side port for data scopes attached to role-permission grants, used
 * when assembling decision facts. Covers both tenant-wide role grants
 * ({@code iam_role_data_scope}) and team-scoped grants
 * ({@code iam_team_role_data_scope}).
 */
public interface ResolvedDataScopeRepository {

    List<ResolvedDataScope> findForRolePermissions(long tenantId, List<Long> rolePermissionIds);

    /**
     * Loads data scopes for both role-permission and team-role-permission
     * grants. Implementations MUST fail closed on unknown IDs.
     */
    default List<ResolvedDataScope> findForRolePermissions(
            long tenantId, List<Long> rolePermissionIds, List<Long> teamRolePermissionIds) {
        return findForRolePermissions(tenantId, rolePermissionIds);
    }
}

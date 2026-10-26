package com.enterprise.iam.authorization.application.port.out;

import com.enterprise.iam.authorization.domain.model.ResolvedFieldPolicy;

import java.util.List;

/**
 * Read-side port for field policies attached to role-permission grants,
 * used when assembling decision facts. Covers both tenant-wide role grants
 * ({@code iam_role_field_policy}) and team-scoped grants
 * ({@code iam_team_role_field_policy}).
 */
public interface ResolvedFieldPolicyRepository {

    List<ResolvedFieldPolicy> findForRolePermissions(long tenantId, List<Long> rolePermissionIds);

    /**
     * Loads field policies for both role-permission and team-role-permission
     * grants. Implementations MUST fail closed on unknown IDs.
     */
    default List<ResolvedFieldPolicy> findForRolePermissions(
            long tenantId, List<Long> rolePermissionIds, List<Long> teamRolePermissionIds) {
        return findForRolePermissions(tenantId, rolePermissionIds);
    }
}

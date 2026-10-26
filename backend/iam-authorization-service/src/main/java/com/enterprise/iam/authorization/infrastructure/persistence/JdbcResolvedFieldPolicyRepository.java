package com.enterprise.iam.authorization.infrastructure.persistence;

import com.enterprise.iam.authorization.application.port.out.ResolvedFieldPolicyRepository;
import com.enterprise.iam.authorization.domain.model.ResolvedFieldPolicy;
import com.enterprise.iam.authorization.infrastructure.persistence.mapper.AuthorizationPolicyMapper;
import com.enterprise.iam.authorization.infrastructure.persistence.row.ResolvedFieldPolicyRow;

import java.util.List;
import java.util.Objects;

/**
 * Reads ACTIVE field policies bound to the given role-permission grants.
 */
public final class JdbcResolvedFieldPolicyRepository implements ResolvedFieldPolicyRepository {

    private final AuthorizationPolicyMapper mapper;

    public JdbcResolvedFieldPolicyRepository(AuthorizationPolicyMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public List<ResolvedFieldPolicy> findForRolePermissions(
            long tenantId, List<Long> rolePermissionIds) {
        return queryRoleFieldPolicies(tenantId, rolePermissionIds, false);
    }

    @Override
    public List<ResolvedFieldPolicy> findForRolePermissions(
            long tenantId, List<Long> rolePermissionIds, List<Long> teamRolePermissionIds) {
        List<ResolvedFieldPolicy> role = queryRoleFieldPolicies(tenantId, rolePermissionIds, false);
        List<ResolvedFieldPolicy> team = queryRoleFieldPolicies(tenantId, teamRolePermissionIds, true);
        if (role.isEmpty()) {
            return team;
        }
        if (team.isEmpty()) {
            return role;
        }
        List<ResolvedFieldPolicy> merged = new java.util.ArrayList<>(role.size() + team.size());
        merged.addAll(role);
        merged.addAll(team);
        return merged;
    }

    private List<ResolvedFieldPolicy> queryRoleFieldPolicies(
            long tenantId, List<Long> permissionIds, boolean teamRole) {
        if (permissionIds == null || permissionIds.isEmpty()) {
            return List.of();
        }
        List<ResolvedFieldPolicyRow> rows = teamRole
                ? mapper.selectTeamRoleFieldPolicies(tenantId, permissionIds)
                : mapper.selectRoleFieldPolicies(tenantId, permissionIds);
        return rows.stream()
                .map(row -> new ResolvedFieldPolicy(
                        row.permissionId(), row.fieldId(), row.readable(), row.writable(),
                        row.hidden(), row.maskStrategyId(), row.priority()))
                .toList();
    }
}

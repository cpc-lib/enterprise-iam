package com.enterprise.iam.authorization.infrastructure.persistence;

import com.enterprise.iam.authorization.application.port.out.ResolvedDataScopeRepository;
import com.enterprise.iam.authorization.domain.model.ResolvedDataScope;
import com.enterprise.iam.authorization.infrastructure.persistence.mapper.AuthorizationPolicyMapper;
import com.enterprise.iam.authorization.infrastructure.persistence.row.ResolvedDataScopeRow;
import com.enterprise.iam.authorization.infrastructure.persistence.row.ScopeTeamRow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Reads ACTIVE data scopes bound to the given role-permission and
 * team-role-permission grants, including the concrete team lists for
 * SPECIFIED_TEAM / TEAM_AND_CHILDREN.
 */
public final class JdbcResolvedDataScopeRepository implements ResolvedDataScopeRepository {

    private final AuthorizationPolicyMapper mapper;

    public JdbcResolvedDataScopeRepository(AuthorizationPolicyMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public List<ResolvedDataScope> findForRolePermissions(
            long tenantId, List<Long> rolePermissionIds) {
        return queryRoleDataScopes(tenantId, rolePermissionIds, false);
    }

    @Override
    public List<ResolvedDataScope> findForRolePermissions(
            long tenantId, List<Long> rolePermissionIds, List<Long> teamRolePermissionIds) {
        List<ResolvedDataScope> role = queryRoleDataScopes(tenantId, rolePermissionIds, false);
        List<ResolvedDataScope> team = queryRoleDataScopes(tenantId, teamRolePermissionIds, true);
        if (role.isEmpty()) {
            return team;
        }
        if (team.isEmpty()) {
            return role;
        }
        List<ResolvedDataScope> merged = new ArrayList<>(role.size() + team.size());
        merged.addAll(role);
        merged.addAll(team);
        return merged;
    }

    private List<ResolvedDataScope> queryRoleDataScopes(
            long tenantId,
            List<Long> permissionIds,
            boolean teamRole) {
        if (permissionIds == null || permissionIds.isEmpty()) {
            return List.of();
        }
        List<ResolvedDataScopeRow> rows = teamRole
                ? mapper.selectTeamRoleDataScopes(tenantId, permissionIds)
                : mapper.selectRoleDataScopes(tenantId, permissionIds);
        if (rows.isEmpty()) {
            return List.of();
        }

        Map<Long, List<Long>> teamsByBinding = loadTeams(tenantId,
                rows.stream().map(ResolvedDataScopeRow::bindingId).distinct().toList(), teamRole);
        return rows.stream()
                .map(row -> new ResolvedDataScope(
                        row.permissionId(),
                        row.dataScopeId(),
                        row.scopeCode(),
                        row.scopeType(),
                        row.customPolicyId(),
                        row.mergeMode(),
                        teamsByBinding.getOrDefault(row.bindingId(), List.of())))
                .toList();
    }

    private Map<Long, List<Long>> loadTeams(
            long tenantId, List<Long> bindingIds, boolean teamRole) {
        List<ScopeTeamRow> rows = teamRole
                ? mapper.selectTeamRoleDataScopeTeams(tenantId, bindingIds)
                : mapper.selectRoleDataScopeTeams(tenantId, bindingIds);
        Map<Long, List<Long>> result = new HashMap<>();
        for (ScopeTeamRow row : rows) {
            result.computeIfAbsent(row.bindingId(), key -> new ArrayList<>()).add(row.teamId());
        }
        return result;
    }
}

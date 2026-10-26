package com.enterprise.iam.authorization.application.service;

import com.enterprise.iam.authorization.application.port.out.AuthorizationPlanCache;
import com.enterprise.iam.authorization.application.port.out.PermissionVersionRepository;
import com.enterprise.iam.authorization.application.port.out.TeamRolePermissionRepository;

import java.util.List;
import java.util.Objects;

public final class ReplaceTeamRolePermissionsUseCase {

    private final TeamRolePermissionRepository teamRolePermissionRepository;
    private final PermissionVersionRepository permissionVersionRepository;
    private final AuthorizationPlanCache planCache;

    public ReplaceTeamRolePermissionsUseCase(
            TeamRolePermissionRepository teamRolePermissionRepository,
            PermissionVersionRepository permissionVersionRepository) {
        this(teamRolePermissionRepository, permissionVersionRepository, null);
    }

    public ReplaceTeamRolePermissionsUseCase(
            TeamRolePermissionRepository teamRolePermissionRepository,
            PermissionVersionRepository permissionVersionRepository,
            AuthorizationPlanCache planCache) {
        this.teamRolePermissionRepository = Objects.requireNonNull(teamRolePermissionRepository);
        this.permissionVersionRepository = Objects.requireNonNull(permissionVersionRepository);
        this.planCache = planCache;
    }

    public Result replace(long tenantId, long teamRoleId, List<Long> permissionIds, long expectedVersion) {
        Objects.requireNonNull(permissionIds, "permissionIds must not be null");
        teamRolePermissionRepository.replacePermissions(tenantId, teamRoleId, permissionIds, expectedVersion);
        long newPermissionVersion = permissionVersionRepository.incrementTenantPermissionVersion(
                tenantId, "TEAM_ROLE_PERMISSION_REPLACE");
        if (planCache != null) {
            planCache.evictTenant(tenantId);
        }
        return new Result(expectedVersion + 1, newPermissionVersion);
    }

    public record Result(long bindingVersion, long permissionVersion) {
    }
}

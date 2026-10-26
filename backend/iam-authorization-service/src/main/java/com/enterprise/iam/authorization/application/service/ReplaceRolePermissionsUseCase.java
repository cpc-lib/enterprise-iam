package com.enterprise.iam.authorization.application.service;

import com.enterprise.iam.authorization.application.port.out.AuthorizationPlanCache;
import com.enterprise.iam.authorization.application.port.out.PermissionVersionRepository;
import com.enterprise.iam.authorization.application.port.out.RolePermissionRepository;
import com.enterprise.iam.common.web.json.NumericLong;

import java.util.List;
import java.util.Objects;

public final class ReplaceRolePermissionsUseCase {

    private final RolePermissionRepository rolePermissionRepository;
    private final PermissionVersionRepository permissionVersionRepository;
    private final AuthorizationPlanCache planCache;

    public ReplaceRolePermissionsUseCase(
            RolePermissionRepository rolePermissionRepository,
            PermissionVersionRepository permissionVersionRepository) {
        this(rolePermissionRepository, permissionVersionRepository, null);
    }

    public ReplaceRolePermissionsUseCase(
            RolePermissionRepository rolePermissionRepository,
            PermissionVersionRepository permissionVersionRepository,
            AuthorizationPlanCache planCache) {
        this.rolePermissionRepository = Objects.requireNonNull(rolePermissionRepository);
        this.permissionVersionRepository = Objects.requireNonNull(permissionVersionRepository);
        this.planCache = planCache;
    }

    public Result replace(long tenantId, long roleId, List<Long> permissionIds, long expectedRoleVersion) {
        Objects.requireNonNull(permissionIds, "permissionIds must not be null");
        rolePermissionRepository.replacePermissions(tenantId, roleId, permissionIds, expectedRoleVersion);
        long newPermissionVersion = permissionVersionRepository.incrementTenantPermissionVersion(
                tenantId, "ROLE_PERMISSION_REPLACE");
        if (planCache != null) {
            planCache.evictTenant(tenantId);
        }
        return new Result(roleId, expectedRoleVersion + 1, newPermissionVersion);
    }

    public record Result(long roleId, @NumericLong long roleVersion,
                         @NumericLong long permissionVersion) {
    }
}

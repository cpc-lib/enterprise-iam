package com.enterprise.iam.authorization.application.service;

import com.enterprise.iam.authorization.application.port.out.AuthorizationPlanCache;
import com.enterprise.iam.authorization.application.port.out.PermissionVersionRepository;
import com.enterprise.iam.authorization.application.port.out.RolePermissionDataScopeRepository;
import com.enterprise.iam.authorization.application.port.out.RolePermissionDataScopeRepository.DataScopeBindingInput;

import java.util.List;
import java.util.Objects;

public final class ReplaceRolePermissionDataScopesUseCase {

    private final RolePermissionDataScopeRepository dataScopeRepository;
    private final PermissionVersionRepository permissionVersionRepository;
    private final AuthorizationPlanCache planCache;

    public ReplaceRolePermissionDataScopesUseCase(
            RolePermissionDataScopeRepository dataScopeRepository,
            PermissionVersionRepository permissionVersionRepository) {
        this(dataScopeRepository, permissionVersionRepository, null);
    }

    public ReplaceRolePermissionDataScopesUseCase(
            RolePermissionDataScopeRepository dataScopeRepository,
            PermissionVersionRepository permissionVersionRepository,
            AuthorizationPlanCache planCache) {
        this.dataScopeRepository = Objects.requireNonNull(dataScopeRepository);
        this.permissionVersionRepository = Objects.requireNonNull(permissionVersionRepository);
        this.planCache = planCache;
    }

    public Result replace(long tenantId, long rolePermissionId, List<DataScopeBindingInput> bindings, long expectedBindingVersion) {
        Objects.requireNonNull(bindings, "bindings must not be null");
        dataScopeRepository.replaceDataScopes(tenantId, rolePermissionId, bindings, expectedBindingVersion);
        long newPermissionVersion = permissionVersionRepository.incrementTenantPermissionVersion(
                tenantId, "ROLE_PERMISSION_DATA_SCOPE_REPLACE");
        if (planCache != null) {
            planCache.evictTenant(tenantId);
        }
        return new Result(expectedBindingVersion + 1, newPermissionVersion);
    }

    public record Result(long bindingVersion, long permissionVersion) {
    }
}

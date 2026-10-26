package com.enterprise.iam.authorization.application.service;

import com.enterprise.iam.authorization.application.port.out.AuthorizationPlanCache;
import com.enterprise.iam.authorization.application.port.out.PermissionVersionRepository;
import com.enterprise.iam.authorization.application.port.out.RolePermissionFieldPolicyRepository;

import java.util.List;
import java.util.Objects;

public final class ReplaceRolePermissionFieldPoliciesUseCase {

    private final RolePermissionFieldPolicyRepository fieldPolicyRepository;
    private final PermissionVersionRepository permissionVersionRepository;
    private final AuthorizationPlanCache planCache;

    public ReplaceRolePermissionFieldPoliciesUseCase(
            RolePermissionFieldPolicyRepository fieldPolicyRepository,
            PermissionVersionRepository permissionVersionRepository) {
        this(fieldPolicyRepository, permissionVersionRepository, null);
    }

    public ReplaceRolePermissionFieldPoliciesUseCase(
            RolePermissionFieldPolicyRepository fieldPolicyRepository,
            PermissionVersionRepository permissionVersionRepository,
            AuthorizationPlanCache planCache) {
        this.fieldPolicyRepository = Objects.requireNonNull(fieldPolicyRepository);
        this.permissionVersionRepository = Objects.requireNonNull(permissionVersionRepository);
        this.planCache = planCache;
    }

    public Result replace(long tenantId, long rolePermissionId, List<Long> fieldPolicyIds, long expectedBindingVersion) {
        Objects.requireNonNull(fieldPolicyIds, "fieldPolicyIds must not be null");
        fieldPolicyRepository.replaceFieldPolicies(tenantId, rolePermissionId, fieldPolicyIds, expectedBindingVersion);
        long newPermissionVersion = permissionVersionRepository.incrementTenantPermissionVersion(
                tenantId, "ROLE_PERMISSION_FIELD_POLICY_REPLACE");
        if (planCache != null) {
            planCache.evictTenant(tenantId);
        }
        return new Result(expectedBindingVersion + 1, newPermissionVersion);
    }

    public record Result(long bindingVersion, long permissionVersion) {
    }
}

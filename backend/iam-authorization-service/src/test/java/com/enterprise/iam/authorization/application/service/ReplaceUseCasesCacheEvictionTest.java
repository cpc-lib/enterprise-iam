package com.enterprise.iam.authorization.application.service;

import com.enterprise.iam.authorization.application.port.out.AuthorizationPlanCache;
import com.enterprise.iam.authorization.application.port.out.PermissionVersionRepository;
import com.enterprise.iam.authorization.application.port.out.RolePermissionDataScopeRepository;
import com.enterprise.iam.authorization.application.port.out.RolePermissionFieldPolicyRepository;
import com.enterprise.iam.authorization.application.port.out.RolePermissionRepository;
import com.enterprise.iam.authorization.application.port.out.TeamRolePermissionRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReplaceUseCasesCacheEvictionTest {

    private final RolePermissionRepository rolePermissionRepository =
            mock(RolePermissionRepository.class);
    private final TeamRolePermissionRepository teamRolePermissionRepository =
            mock(TeamRolePermissionRepository.class);
    private final RolePermissionDataScopeRepository dataScopeRepository =
            mock(RolePermissionDataScopeRepository.class);
    private final RolePermissionFieldPolicyRepository fieldPolicyRepository =
            mock(RolePermissionFieldPolicyRepository.class);
    private final PermissionVersionRepository permissionVersionRepository =
            mock(PermissionVersionRepository.class);
    private final AuthorizationPlanCache planCache = mock(AuthorizationPlanCache.class);

    @Test
    void allFourReplaceUseCasesEvictTenantCacheAfterVersionBump() {
        when(permissionVersionRepository.incrementTenantPermissionVersion(anyLong(), eq("ROLE_PERMISSION_REPLACE")))
                .thenReturn(2L);

        new ReplaceRolePermissionsUseCase(
                rolePermissionRepository, permissionVersionRepository, planCache)
                .replace(7L, 1L, List.of(10L), 0L);
        new ReplaceTeamRolePermissionsUseCase(
                teamRolePermissionRepository, permissionVersionRepository, planCache)
                .replace(7L, 1L, List.of(10L), 0L);
        new ReplaceRolePermissionDataScopesUseCase(
                dataScopeRepository, permissionVersionRepository, planCache)
                .replace(7L, 1L, List.of(), 0L);
        new ReplaceRolePermissionFieldPoliciesUseCase(
                fieldPolicyRepository, permissionVersionRepository, planCache)
                .replace(7L, 1L, List.of(20L), 0L);

        verify(planCache, org.mockito.Mockito.times(4)).evictTenant(7L);
    }
}

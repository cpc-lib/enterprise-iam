package com.enterprise.iam.identity.application.service;

import com.enterprise.iam.identity.application.port.out.RoleRepository;

import java.util.List;
import java.util.Objects;

public final class GetRoleMenusUseCase {

    private final RoleRepository roleRepository;

    public GetRoleMenusUseCase(RoleRepository roleRepository) {
        this.roleRepository = Objects.requireNonNull(roleRepository);
    }

    public List<Long> getRoleMenus(long tenantId, long roleId) {
        long resolvedTenantId = tenantId;
        Long rTenant = roleRepository.findTenantIdByRoleId(roleId);
        if (rTenant != null && rTenant > 0) {
            resolvedTenantId = rTenant;
        }
        return roleRepository.findRoleMenuIds(resolvedTenantId, roleId);
    }
}

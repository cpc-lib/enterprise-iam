package com.enterprise.iam.identity.application.service;

import com.enterprise.iam.identity.application.port.out.RoleRepository;
import com.enterprise.iam.identity.domain.model.Role;

import java.util.List;
import java.util.Objects;

public final class UpdateRoleMenusUseCase {

    private final RoleRepository roleRepository;

    public UpdateRoleMenusUseCase(RoleRepository roleRepository) {
        this.roleRepository = Objects.requireNonNull(roleRepository);
    }

    public void updateRoleMenus(long callerTenantId, long roleId, List<Long> menuIds) {
        Role role = roleRepository.findById(callerTenantId, roleId)
                .orElseGet(() -> {
                    Long rTenant = roleRepository.findTenantIdByRoleId(roleId);
                    if (rTenant != null && rTenant > 0) {
                        return roleRepository.findById(rTenant, roleId).orElse(null);
                    }
                    return null;
                });
        if (role == null) {
            throw new IllegalArgumentException("角色不存在 (ID: " + roleId + ")");
        }
        if (role.tenantId() != callerTenantId) {
            throw new SecurityException("无权修改非本租户创建角色的菜单权限 (所属租户: " + role.tenantId() + ")");
        }
        roleRepository.replaceRoleMenus(role.tenantId(), roleId, menuIds);
    }
}

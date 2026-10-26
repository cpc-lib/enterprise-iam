package com.enterprise.iam.identity.application.service;

import com.enterprise.iam.identity.application.port.out.RoleRepository;
import com.enterprise.iam.identity.domain.model.Role;

import java.util.Objects;

public class DeleteRoleUseCase {

    private final RoleRepository roleRepository;

    public DeleteRoleUseCase(RoleRepository roleRepository) {
        this.roleRepository = Objects.requireNonNull(roleRepository, "roleRepository must not be null");
    }

    public void delete(long callerTenantId, long roleId, Long expectedVersion) {
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
            throw new SecurityException("无权删除非本租户创建的角色 (所属租户: " + role.tenantId() + ")");
        }
        roleRepository.delete(role.tenantId(), roleId, expectedVersion);
    }
}

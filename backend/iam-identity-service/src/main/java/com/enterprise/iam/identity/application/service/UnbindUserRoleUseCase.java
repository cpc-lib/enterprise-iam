package com.enterprise.iam.identity.application.service;

import com.enterprise.iam.identity.application.port.out.RoleRepository;
import com.enterprise.iam.identity.application.port.out.UserRepository;
import com.enterprise.iam.identity.application.port.out.UserRoleRepository;

import java.util.Objects;

public final class UnbindUserRoleUseCase {

    private final UserRoleRepository userRoleRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    public UnbindUserRoleUseCase(UserRoleRepository userRoleRepository) {
        this(userRoleRepository, null, null);
    }

    public UnbindUserRoleUseCase(
            UserRoleRepository userRoleRepository,
            UserRepository userRepository,
            RoleRepository roleRepository) {
        this.userRoleRepository = Objects.requireNonNull(userRoleRepository);
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
    }

    public void unbind(long callerTenantId, long userId, long roleId) {
        if (roleRepository != null) {
            Long roleTenant = roleRepository.findTenantIdByRoleId(roleId);
            if (roleTenant != null && roleTenant > 0 && roleTenant != callerTenantId) {
                throw new SecurityException("无权移除未由本租户管理的角色成员 (角色所属租户: " + roleTenant + ")");
            }
        }
        long resolvedTenantId = callerTenantId;
        if (userRepository != null) {
            Long uTenant = userRepository.findTenantIdByUserId(userId);
            if (uTenant != null && uTenant > 0) {
                resolvedTenantId = uTenant;
            }
        }
        if (callerTenantId == 10001L && resolvedTenantId != 10001L) {
            boolean isTenantAdmin = userRepository != null && userRepository.isTenantAdmin(resolvedTenantId, userId);
            if (!isTenantAdmin) {
                throw new SecurityException("超级系统管理员只能管理租户管理员，不能移除租户下其他普通用户的角色");
            }
        }
        userRoleRepository.revoke(resolvedTenantId, userId, roleId);
    }
}

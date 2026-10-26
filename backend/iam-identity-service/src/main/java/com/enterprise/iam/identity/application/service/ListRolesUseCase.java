package com.enterprise.iam.identity.application.service;

import com.enterprise.iam.identity.application.port.out.RoleRepository;
import com.enterprise.iam.identity.domain.model.Role;

import java.util.List;
import java.util.Objects;

public class ListRolesUseCase {

    private final RoleRepository roleRepository;

    public ListRolesUseCase(RoleRepository roleRepository) {
        this.roleRepository = Objects.requireNonNull(roleRepository);
    }

    public List<Role> listRoles(Long tenantId) {
        return roleRepository.findAll(tenantId, null);
    }

    public List<Role> listRoles(Long tenantId, Long userId) {
        return roleRepository.findAll(tenantId, userId);
    }

    public List<Role> listRolesByUser(Long tenantId, long userId) {
        return roleRepository.findRolesByUserId(tenantId, userId);
    }
}

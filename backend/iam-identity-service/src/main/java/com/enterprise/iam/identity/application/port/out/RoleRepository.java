package com.enterprise.iam.identity.application.port.out;

import com.enterprise.iam.identity.domain.model.Role;

import java.util.List;
import java.util.Optional;

public interface RoleRepository {

    Role save(Role role);

    Optional<Role> findById(long tenantId, long roleId);

    Optional<Role> findByCode(long tenantId, String roleCode);

    List<Role> findAll(Long tenantId);

    List<Role> findAll(Long tenantId, Long userId);

    List<Role> findRolesByUserId(Long tenantId, long userId);

    List<Long> findRoleMenuIds(long tenantId, long roleId);

    void replaceRoleMenus(long tenantId, long roleId, List<Long> menuIds);

    void updateStatus(long tenantId, long roleId, com.enterprise.iam.identity.domain.model.RoleStatus status, Long expectedVersion);

    void delete(long tenantId, long roleId, Long expectedVersion);

    Long findTenantIdByRoleId(long roleId);
}

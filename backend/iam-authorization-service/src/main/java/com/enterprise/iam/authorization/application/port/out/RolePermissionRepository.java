package com.enterprise.iam.authorization.application.port.out;

import java.util.List;

public interface RolePermissionRepository {

    void replacePermissions(long tenantId, long roleId, List<Long> permissionIds, long expectedRoleVersion);

    List<Long> findActivePermissionIds(long tenantId, long roleId);
}

package com.enterprise.iam.authorization.application.port.out;

import java.util.List;

public interface TeamRolePermissionRepository {

    void replacePermissions(long tenantId, long teamRoleId, List<Long> permissionIds, long expectedVersion);

    List<Long> findActivePermissionIds(long tenantId, long teamRoleId);
}

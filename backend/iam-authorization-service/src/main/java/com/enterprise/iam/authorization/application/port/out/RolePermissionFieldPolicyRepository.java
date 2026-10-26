package com.enterprise.iam.authorization.application.port.out;

import java.util.List;

public interface RolePermissionFieldPolicyRepository {

    void replaceFieldPolicies(long tenantId, long rolePermissionId, List<Long> fieldPolicyIds, long expectedBindingVersion);
}

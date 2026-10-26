package com.enterprise.iam.authorization.application.port.out;

import java.util.List;

public interface RolePermissionDataScopeRepository {

    record DataScopeBindingInput(
            long dataScopeId,
            String mergeMode,
            List<Long> teamIds,
            boolean includeChildren) {
    }

    void replaceDataScopes(long tenantId, long rolePermissionId, List<DataScopeBindingInput> bindings, long expectedBindingVersion);
}

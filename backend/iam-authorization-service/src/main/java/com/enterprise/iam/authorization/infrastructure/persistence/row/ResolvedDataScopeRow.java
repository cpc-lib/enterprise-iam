package com.enterprise.iam.authorization.infrastructure.persistence.row;

public record ResolvedDataScopeRow(
        long bindingId,
        long permissionId,
        long dataScopeId,
        String scopeCode,
        String scopeType,
        Long customPolicyId,
        String mergeMode) {
}

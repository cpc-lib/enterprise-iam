package com.enterprise.iam.authorization.infrastructure.persistence.row;

public record ResolvedFieldPolicyRow(
        long permissionId,
        long fieldId,
        boolean readable,
        boolean writable,
        boolean hidden,
        Long maskStrategyId,
        int priority) {
}

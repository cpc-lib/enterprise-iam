package com.enterprise.iam.authorization.infrastructure.persistence.row;

import java.time.Instant;

public record AbacPolicyRow(
        long id,
        long tenantId,
        String policyCode,
        String policyName,
        String resourceType,
        String operation,
        String effect,
        int priority,
        String conditionExpr,
        String description,
        String status,
        long version,
        Instant createdAt,
        Instant updatedAt) {
}

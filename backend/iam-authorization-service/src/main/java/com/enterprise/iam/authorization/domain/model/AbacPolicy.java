package com.enterprise.iam.authorization.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * V2.0 ABAC Policy domain model.
 */
public record AbacPolicy(
        long id,
        long tenantId,
        String policyCode,
        String policyName,
        String resourceType,
        String operation,
        GrantEffect effect,
        int priority,
        String conditionExpr,
        String description,
        String status,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_DISABLED = "DISABLED";

    public AbacPolicy {
        if (id <= 0 || tenantId <= 0) {
            throw new IllegalArgumentException("id and tenantId must be positive");
        }
        policyCode = requireNonBlank(policyCode, "policyCode");
        policyName = requireNonBlank(policyName, "policyName");
        resourceType = requireNonBlank(resourceType, "resourceType");
        operation = requireNonBlank(operation, "operation");
        Objects.requireNonNull(effect, "effect must not be null");
        conditionExpr = requireNonBlank(conditionExpr, "conditionExpr");
        status = status == null || status.isBlank() ? STATUS_ACTIVE : status.trim().toUpperCase();
        if (!STATUS_ACTIVE.equals(status) && !STATUS_DISABLED.equals(status)) {
            throw new IllegalArgumentException("status must be ACTIVE or DISABLED");
        }
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
    }

    public AbacPolicy(
            long id,
            long tenantId,
            String policyCode,
            String policyName,
            String resourceType,
            String operation,
            GrantEffect effect,
            int priority,
            String conditionExpr,
            String description,
            String status,
            long version) {
        this(id, tenantId, policyCode, policyName, resourceType, operation,
                effect, priority, conditionExpr, description, status, version,
                Instant.now(), Instant.now());
    }

    public boolean isActive() {
        return STATUS_ACTIVE.equals(status);
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return trimmed;
    }
}

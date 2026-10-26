package com.enterprise.iam.identity.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record SodPolicy(
        long id,
        long tenantId,
        String policyCode,
        String policyName,
        String description,
        List<Long> conflictingRoleIds,
        int maxAllowedRoles,
        String enforcementMode,
        String severity,
        String status,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    public SodPolicy {
        Objects.requireNonNull(policyCode, "policyCode must not be null");
        Objects.requireNonNull(policyName, "policyName must not be null");
        Objects.requireNonNull(conflictingRoleIds, "conflictingRoleIds must not be null");
        Objects.requireNonNull(enforcementMode, "enforcementMode must not be null");
        Objects.requireNonNull(severity, "severity must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        if (maxAllowedRoles < 1) {
            throw new IllegalArgumentException("maxAllowedRoles must be >= 1");
        }
        conflictingRoleIds = List.copyOf(conflictingRoleIds);
    }

    public boolean isActiveStaticPolicy() {
        return "ACTIVE".equals(status) && "STATIC".equals(enforcementMode);
    }
}

package com.enterprise.iam.identity.domain.model;

import java.util.List;

public record SodConflictCheckResult(
        boolean violated,
        String violatedPolicyCode,
        String violatedPolicyName,
        List<Long> conflictingRoleIds) {

    public static SodConflictCheckResult pass() {
        return new SodConflictCheckResult(false, null, null, List.of());
    }

    public static SodConflictCheckResult violated(
            String policyCode,
            String policyName,
            List<Long> conflictingRoleIds) {
        return new SodConflictCheckResult(true, policyCode, policyName, List.copyOf(conflictingRoleIds));
    }
}

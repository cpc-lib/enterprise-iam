package com.enterprise.iam.authorization.domain.model;

import java.util.Objects;

/**
 * A field-level grant projected from the sharing-service ACL. The engine
 * resolves these into typed obligations when a share grant matches so that
 * consuming services can apply field-level masking via FieldPolicyEnforcer.
 */
public record ShareFieldGrant(
        long fieldId,
        boolean readable,
        boolean writable,
        boolean hidden,
        Long maskStrategyId) {

    public ShareFieldGrant {
        if (fieldId <= 0) {
            throw new IllegalArgumentException("fieldId must be positive");
        }
        if (hidden && (readable || writable)) {
            throw new IllegalArgumentException("hidden field cannot be readable or writable");
        }
        if (maskStrategyId != null && maskStrategyId <= 0) {
            throw new IllegalArgumentException("maskStrategyId must be positive or null");
        }
    }
}

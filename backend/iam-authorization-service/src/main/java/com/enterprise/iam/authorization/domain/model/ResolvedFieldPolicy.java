package com.enterprise.iam.authorization.domain.model;

/**
 * A field policy bound to a role-permission grant, read at decision time.
 * Priority decides multi-policy overlap (higher wins); ties resolve to the
 * most restrictive result per SPEC 38 §82.
 */
public record ResolvedFieldPolicy(
        long rolePermissionId,
        long fieldId,
        boolean readable,
        boolean writable,
        boolean hidden,
        Long maskStrategyId,
        int priority) {

    public ResolvedFieldPolicy {
        if (rolePermissionId <= 0 || fieldId <= 0) {
            throw new IllegalArgumentException("ids must be positive");
        }
        if (hidden && (readable || writable)) {
            throw new IllegalArgumentException("hidden field policy cannot be readable or writable");
        }
        if (maskStrategyId != null && maskStrategyId <= 0) {
            throw new IllegalArgumentException("maskStrategyId must be positive");
        }
    }
}

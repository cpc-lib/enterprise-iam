package com.enterprise.iam.auth.domain.model;

public record ResolvedLoginIdentity(long tenantId, long userId, boolean active) {

    public ResolvedLoginIdentity {
        requirePositive(tenantId, "tenantId");
        requirePositive(userId, "userId");
    }

    private static void requirePositive(long value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}

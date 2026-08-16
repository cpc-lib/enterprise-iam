package com.enterprise.iam.authorization.domain.model;

import java.util.List;

public record AuthorizationFacts(
        boolean authoritativeAvailable,
        long tenantId,
        long resourceId,
        long operationId,
        long authoritativePermissionVersion,
        boolean resourceOperationEnabled,
        List<ResolvedGrant> grants) {

    public AuthorizationFacts {
        grants = grants == null ? List.of() : List.copyOf(grants);
        if (authoritativeAvailable) {
            requirePositive(tenantId, "tenantId");
            requirePositive(resourceId, "resourceId");
            requirePositive(operationId, "operationId");
            if (authoritativePermissionVersion < 0) {
                throw new IllegalArgumentException("authoritativePermissionVersion must not be negative");
            }
        }
    }

    public static AuthorizationFacts unavailable(AuthorizationRequest request) {
        return new AuthorizationFacts(
                false,
                request.tenantId(),
                request.resourceId(),
                request.operationId(),
                -1,
                false,
                List.of());
    }

    private static void requirePositive(long value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}

package com.enterprise.iam.authorization.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * A grant already resolved for the evaluated subject by the application port.
 * The domain engine still rechecks every policy-relevant identity dimension.
 */
public record ResolvedGrant(
        long grantId,
        long tenantId,
        long subjectId,
        long resourceId,
        long operationId,
        GrantEffect effect,
        boolean active,
        Instant activeFrom,
        Instant expiresAt) {

    public ResolvedGrant {
        requirePositive(grantId, "grantId");
        requirePositive(tenantId, "tenantId");
        requirePositive(subjectId, "subjectId");
        requirePositive(resourceId, "resourceId");
        requirePositive(operationId, "operationId");
        Objects.requireNonNull(effect, "effect must not be null");
        Objects.requireNonNull(activeFrom, "activeFrom must not be null");
        if (expiresAt != null && !expiresAt.isAfter(activeFrom)) {
            throw new IllegalArgumentException("expiresAt must be after activeFrom");
        }
    }

    public boolean matches(AuthorizationRequest request, Instant evaluatedAt) {
        return active
                && tenantId == request.tenantId()
                && subjectId == request.subjectId()
                && resourceId == request.resourceId()
                && operationId == request.operationId()
                && !evaluatedAt.isBefore(activeFrom)
                && (expiresAt == null || evaluatedAt.isBefore(expiresAt));
    }

    private static void requirePositive(long value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}

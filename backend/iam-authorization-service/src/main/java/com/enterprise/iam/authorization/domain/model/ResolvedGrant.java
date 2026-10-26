package com.enterprise.iam.authorization.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * A grant already resolved for the evaluated subject by the application port.
 * The domain engine still rechecks every policy-relevant identity dimension.
 *
 * <p>{@code teamId} is null for tenant-wide role grants
 * ({@code iam_role_permission}) and non-null for team-scoped grants
 * ({@code iam_team_role_permission}); the latter match only when the request
 * context carries that team.
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
        Instant expiresAt,
        Long teamId) {

    public ResolvedGrant {
        requirePositive(grantId, "grantId");
        requirePositive(tenantId, "tenantId");
        requirePositive(subjectId, "subjectId");
        requirePositive(resourceId, "resourceId");
        requirePositive(operationId, "operationId");
        Objects.requireNonNull(effect, "effect must not be null");
        Objects.requireNonNull(activeFrom, "activeFrom must not be null");
        if (teamId != null && teamId <= 0) {
            throw new IllegalArgumentException("teamId must be positive or null");
        }
        if (expiresAt != null && !expiresAt.isAfter(activeFrom)) {
            throw new IllegalArgumentException("expiresAt must be after activeFrom");
        }
    }

    /** Backward-compatible constructor for tenant-wide role grants. */
    public ResolvedGrant(
            long grantId,
            long tenantId,
            long subjectId,
            long resourceId,
            long operationId,
            GrantEffect effect,
            boolean active,
            Instant activeFrom,
            Instant expiresAt) {
        this(grantId, tenantId, subjectId, resourceId, operationId,
                effect, active, activeFrom, expiresAt, null);
    }

    public boolean matches(AuthorizationRequest request, Instant evaluatedAt) {
        if (!active
                || tenantId != request.tenantId()
                || subjectId != request.subjectId()
                || resourceId != request.resourceId()
                || operationId != request.operationId()
                || evaluatedAt.isBefore(activeFrom)
                || (expiresAt != null && !evaluatedAt.isBefore(expiresAt))) {
            return false;
        }
        if (teamId != null) {
            List<Long> teamIds = TeamIds.from(request.context());
            return teamIds.contains(teamId);
        }
        return true;
    }

    private static void requirePositive(long value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}

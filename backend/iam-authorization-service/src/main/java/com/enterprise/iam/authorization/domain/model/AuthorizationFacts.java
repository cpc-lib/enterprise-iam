package com.enterprise.iam.authorization.domain.model;

import java.util.List;

public record AuthorizationFacts(
        boolean authoritativeAvailable,
        long tenantId,
        long resourceId,
        long operationId,
        long authoritativePermissionVersion,
        boolean resourceOperationEnabled,
        List<ResolvedGrant> grants,
        List<ShareGrant> shareGrants,
        boolean shareProjectionFresh,
        long expectedShareEpoch,
        long appliedShareEpoch,
        List<ResolvedFieldPolicy> roleFieldPolicies,
        List<ResolvedDataScope> roleDataScopes,
        List<ResolvedMaskStrategy> maskStrategies,
        List<AbacPolicy> abacPolicies) {

    public AuthorizationFacts {
        grants = grants == null ? List.of() : List.copyOf(grants);
        shareGrants = shareGrants == null ? List.of() : List.copyOf(shareGrants);
        roleFieldPolicies = roleFieldPolicies == null
                ? List.of() : List.copyOf(roleFieldPolicies);
        roleDataScopes = roleDataScopes == null
                ? List.of() : List.copyOf(roleDataScopes);
        maskStrategies = maskStrategies == null
                ? List.of() : List.copyOf(maskStrategies);
        abacPolicies = abacPolicies == null
                ? List.of() : List.copyOf(abacPolicies);
        if (authoritativeAvailable) {
            requirePositive(tenantId, "tenantId");
            requirePositive(resourceId, "resourceId");
            requirePositive(operationId, "operationId");
            if (authoritativePermissionVersion < 0) {
                throw new IllegalArgumentException("authoritativePermissionVersion must not be negative");
            }
            if (expectedShareEpoch < 0 || appliedShareEpoch < 0) {
                throw new IllegalArgumentException("share epochs must not be negative");
            }
        }
    }

    /** Backward-compatible facts without ABAC policies. */
    public AuthorizationFacts(
            boolean authoritativeAvailable,
            long tenantId,
            long resourceId,
            long operationId,
            long authoritativePermissionVersion,
            boolean resourceOperationEnabled,
            List<ResolvedGrant> grants,
            List<ShareGrant> shareGrants,
            boolean shareProjectionFresh,
            long expectedShareEpoch,
            long appliedShareEpoch,
            List<ResolvedFieldPolicy> roleFieldPolicies,
            List<ResolvedDataScope> roleDataScopes,
            List<ResolvedMaskStrategy> maskStrategies) {
        this(authoritativeAvailable, tenantId, resourceId, operationId,
                authoritativePermissionVersion, resourceOperationEnabled, grants,
                shareGrants, shareProjectionFresh, expectedShareEpoch, appliedShareEpoch,
                roleFieldPolicies, roleDataScopes, maskStrategies, List.of());
    }

    /** Backward-compatible facts without share projection or role policy evidence. */
    public AuthorizationFacts(
            boolean authoritativeAvailable,
            long tenantId,
            long resourceId,
            long operationId,
            long authoritativePermissionVersion,
            boolean resourceOperationEnabled,
            List<ResolvedGrant> grants) {
        this(authoritativeAvailable, tenantId, resourceId, operationId,
                authoritativePermissionVersion, resourceOperationEnabled, grants,
                List.of(), true, 0L, 0L, List.of(), List.of(), List.of(), List.of());
    }

    /** Backward-compatible facts without resolved mask strategy definitions. */
    public AuthorizationFacts(
            boolean authoritativeAvailable,
            long tenantId,
            long resourceId,
            long operationId,
            long authoritativePermissionVersion,
            boolean resourceOperationEnabled,
            List<ResolvedGrant> grants,
            List<ShareGrant> shareGrants,
            boolean shareProjectionFresh,
            long expectedShareEpoch,
            long appliedShareEpoch,
            List<ResolvedFieldPolicy> roleFieldPolicies,
            List<ResolvedDataScope> roleDataScopes) {
        this(authoritativeAvailable, tenantId, resourceId, operationId,
                authoritativePermissionVersion, resourceOperationEnabled, grants,
                shareGrants, shareProjectionFresh, expectedShareEpoch, appliedShareEpoch,
                roleFieldPolicies, roleDataScopes, List.of(), List.of());
    }

    public static AuthorizationFacts unavailable(AuthorizationRequest request) {
        return new AuthorizationFacts(
                false,
                request.tenantId(),
                request.resourceId(),
                request.operationId(),
                -1,
                false,
                List.of(),
                List.of(),
                true,
                0L,
                0L,
                List.of(),
                List.of(),
                List.of(),
                List.of());
    }

    private static void requirePositive(long value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}

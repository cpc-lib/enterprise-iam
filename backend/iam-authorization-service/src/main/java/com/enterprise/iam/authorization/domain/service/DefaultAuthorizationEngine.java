package com.enterprise.iam.authorization.domain.service;

import com.enterprise.iam.authorization.domain.model.AuthorizationDecision;
import com.enterprise.iam.authorization.domain.model.AuthorizationFacts;
import com.enterprise.iam.authorization.domain.model.AuthorizationReason;
import com.enterprise.iam.authorization.domain.model.AuthorizationRequest;
import com.enterprise.iam.authorization.domain.model.AuthorizationResult;
import com.enterprise.iam.authorization.domain.model.GrantEffect;
import com.enterprise.iam.authorization.domain.model.ResolvedGrant;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Phase-01 fail-closed authorization core. Repositories resolve facts; this
 * domain service owns precedence and decision semantics only.
 */
public final class DefaultAuthorizationEngine {

    private final Clock clock;
    private final LongSupplier decisionIdSupplier;

    public DefaultAuthorizationEngine(Clock clock, LongSupplier decisionIdSupplier) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.decisionIdSupplier = Objects.requireNonNull(
                decisionIdSupplier, "decisionIdSupplier must not be null");
    }

    public AuthorizationResult evaluate(AuthorizationRequest request, AuthorizationFacts facts) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(facts, "facts must not be null");
        Instant evaluatedAt = clock.instant();

        if (!facts.authoritativeAvailable()) {
            return deny(request.permissionVersion(), AuthorizationReason.DEPENDENCY_UNAVAILABLE, List.of(), evaluatedAt);
        }
        if (facts.tenantId() != request.tenantId()) {
            return deny(facts.authoritativePermissionVersion(), AuthorizationReason.TENANT_MISMATCH, List.of(), evaluatedAt);
        }
        if (facts.resourceId() != request.resourceId() || facts.operationId() != request.operationId()) {
            return deny(
                    facts.authoritativePermissionVersion(),
                    AuthorizationReason.RESOURCE_CONTEXT_MISMATCH,
                    List.of(),
                    evaluatedAt);
        }
        if (request.permissionVersion() < facts.authoritativePermissionVersion()) {
            return deny(
                    facts.authoritativePermissionVersion(),
                    AuthorizationReason.STALE_PERMISSION_VERSION,
                    List.of(),
                    evaluatedAt);
        }
        if (request.permissionVersion() > facts.authoritativePermissionVersion()) {
            return deny(
                    facts.authoritativePermissionVersion(),
                    AuthorizationReason.PERMISSION_VERSION_MISMATCH,
                    List.of(),
                    evaluatedAt);
        }
        if (!facts.resourceOperationEnabled()) {
            return deny(
                    facts.authoritativePermissionVersion(),
                    AuthorizationReason.RESOURCE_OPERATION_DISABLED,
                    List.of(),
                    evaluatedAt);
        }

        List<ResolvedGrant> matching = facts.grants().stream()
                .filter(grant -> grant.matches(request, evaluatedAt))
                .toList();
        List<Long> explicitDenies = grantIds(matching, GrantEffect.DENY);
        if (!explicitDenies.isEmpty()) {
            return deny(
                    facts.authoritativePermissionVersion(),
                    AuthorizationReason.EXPLICIT_DENY,
                    explicitDenies,
                    evaluatedAt);
        }
        List<Long> allows = grantIds(matching, GrantEffect.ALLOW);
        if (!allows.isEmpty()) {
            return result(
                    AuthorizationDecision.ALLOW,
                    AuthorizationReason.GRANT_ALLOW,
                    facts.authoritativePermissionVersion(),
                    allows,
                    evaluatedAt);
        }
        return deny(
                facts.authoritativePermissionVersion(),
                AuthorizationReason.NO_MATCHING_GRANT,
                List.of(),
                evaluatedAt);
    }

    private List<Long> grantIds(List<ResolvedGrant> grants, GrantEffect effect) {
        return grants.stream()
                .filter(grant -> grant.effect() == effect)
                .map(ResolvedGrant::grantId)
                .distinct()
                .sorted()
                .limit(100)
                .toList();
    }

    private AuthorizationResult deny(
            long permissionVersion,
            AuthorizationReason reason,
            List<Long> matchedGrantIds,
            Instant evaluatedAt) {
        return result(
                AuthorizationDecision.DENY,
                reason,
                Math.max(permissionVersion, 0),
                matchedGrantIds,
                evaluatedAt);
    }

    private AuthorizationResult result(
            AuthorizationDecision decision,
            AuthorizationReason reason,
            long permissionVersion,
            List<Long> matchedGrantIds,
            Instant evaluatedAt) {
        long decisionId = decisionIdSupplier.getAsLong();
        if (decisionId <= 0) {
            throw new IllegalStateException("decisionIdSupplier must return a positive identifier");
        }
        return new AuthorizationResult(
                decision,
                reason,
                decisionId,
                permissionVersion,
                matchedGrantIds,
                Map.of(),
                evaluatedAt);
    }
}

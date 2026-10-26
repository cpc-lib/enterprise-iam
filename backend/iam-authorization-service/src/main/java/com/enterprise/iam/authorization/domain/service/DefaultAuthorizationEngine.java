package com.enterprise.iam.authorization.domain.service;

import com.enterprise.iam.authorization.domain.model.AbacPolicy;
import com.enterprise.iam.authorization.domain.model.AuthorizationDecision;
import com.enterprise.iam.authorization.domain.model.AuthorizationFacts;
import com.enterprise.iam.authorization.domain.model.AuthorizationReason;
import com.enterprise.iam.authorization.domain.model.AuthorizationRequest;
import com.enterprise.iam.authorization.domain.model.AuthorizationResult;
import com.enterprise.iam.authorization.domain.model.GrantEffect;
import com.enterprise.iam.authorization.domain.model.ResolvedDataScope;
import com.enterprise.iam.authorization.domain.model.ResolvedFieldPolicy;
import com.enterprise.iam.authorization.domain.model.ResolvedGrant;
import com.enterprise.iam.authorization.domain.model.ResolvedMaskStrategy;
import com.enterprise.iam.authorization.domain.model.ShareFieldGrant;
import com.enterprise.iam.authorization.domain.model.ShareGrant;
import com.enterprise.iam.authorization.domain.model.TeamIds;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.LongSupplier;

/**
 * Phase-01/V2.0 fail-closed authorization core. Repositories resolve facts; this
 * domain service owns precedence and decision semantics only (including V2.0 ABAC
 * condition evaluation and folder/collection share scopes).
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

        List<AbacPolicy> matchedAbacPolicies = evaluateAbacPolicies(request, facts.abacPolicies());
        List<Long> abacDenies = abacPolicyIds(matchedAbacPolicies, GrantEffect.DENY);
        if (!abacDenies.isEmpty()) {
            return deny(
                    facts.authoritativePermissionVersion(),
                    AuthorizationReason.ABAC_POLICY_DENY,
                    abacDenies,
                    evaluatedAt);
        }

        List<Long> allows = grantIds(matching, GrantEffect.ALLOW);
        if (!allows.isEmpty()) {
            Set<Long> allowIds = new HashSet<>(allows);
            boolean teamRoleOnly = matching.stream()
                    .filter(grant -> grant.effect() == GrantEffect.ALLOW)
                    .allMatch(grant -> grant.teamId() != null && allowIds.contains(grant.grantId()));
            return result(
                    AuthorizationDecision.ALLOW,
                    teamRoleOnly ? AuthorizationReason.TEAM_ROLE_GRANT : AuthorizationReason.GRANT_ALLOW,
                    facts.authoritativePermissionVersion(),
                    allows,
                    buildRoleObligations(facts, allows),
                    evaluatedAt);
        }

        List<Long> abacAllows = abacPolicyIds(matchedAbacPolicies, GrantEffect.ALLOW);
        if (!abacAllows.isEmpty()) {
            return result(
                    AuthorizationDecision.ALLOW,
                    AuthorizationReason.ABAC_POLICY_ALLOW,
                    facts.authoritativePermissionVersion(),
                    abacAllows,
                    evaluatedAt);
        }

        // Spec 16 fail-closed fence: projection rows known to be behind the
        // sharing-service epoch cannot authorize, even if stale rows match.
        if (facts.expectedShareEpoch() > facts.appliedShareEpoch()) {
            return deny(
                    facts.authoritativePermissionVersion(),
                    AuthorizationReason.SHARE_PROJECTION_STALE,
                    List.of(),
                    evaluatedAt);
        }
        List<Long> teamIds = TeamIds.from(request.context());
        List<ShareGrant> matchedShareGrants = facts.shareGrants().stream()
                .filter(grant -> grant.matches(request, teamIds, evaluatedAt))
                .sorted((a, b) -> Long.compare(a.shareId(), b.shareId()))
                .toList();
        List<Long> matchedShares = matchedShareGrants.stream()
                .map(ShareGrant::shareId)
                .distinct()
                .sorted()
                .limit(100)
                .toList();
        if (!matchedShares.isEmpty()) {
            return result(
                    AuthorizationDecision.ALLOW,
                    AuthorizationReason.SHARE_GRANT,
                    facts.authoritativePermissionVersion(),
                    matchedShares,
                    buildFieldGrantObligations(matchedShareGrants, facts.maskStrategies()),
                    evaluatedAt);
        }
        return deny(
                facts.authoritativePermissionVersion(),
                AuthorizationReason.NO_MATCHING_GRANT,
                List.of(),
                evaluatedAt);
    }

    private List<AbacPolicy> evaluateAbacPolicies(AuthorizationRequest request, List<AbacPolicy> policies) {
        if (policies == null || policies.isEmpty()) {
            return List.of();
        }
        Map<String, Object> evalContext = new LinkedHashMap<>();
        if (request.context() != null) {
            evalContext.putAll(request.context());
        }
        evalContext.putIfAbsent("tenantId", request.tenantId());
        evalContext.putIfAbsent("subjectId", request.subjectId());
        evalContext.putIfAbsent("subject.id", request.subjectId());
        evalContext.putIfAbsent("resourceId", request.resourceId());
        evalContext.putIfAbsent("resource.id", request.resourceId());
        evalContext.putIfAbsent("operationId", request.operationId());
        if (request.resourceInstanceId() != null) {
            evalContext.putIfAbsent("resourceInstanceId", request.resourceInstanceId());
            evalContext.putIfAbsent("resource.instanceId", request.resourceInstanceId());
        }
        return policies.stream()
                .filter(p -> p != null && p.isActive() && p.tenantId() == request.tenantId())
                .sorted(Comparator.comparingInt(AbacPolicy::priority).reversed()
                        .thenComparingLong(AbacPolicy::id))
                .filter(p -> ConditionDslEvaluator.evaluate(p.conditionExpr(), evalContext))
                .toList();
    }

    private List<Long> abacPolicyIds(List<AbacPolicy> policies, GrantEffect effect) {
        return policies.stream()
                .filter(p -> p.effect() == effect)
                .map(AbacPolicy::id)
                .distinct()
                .sorted()
                .limit(100)
                .toList();
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
        return result(decision, reason, permissionVersion, matchedGrantIds, Map.of(), evaluatedAt);
    }

    private AuthorizationResult result(
            AuthorizationDecision decision,
            AuthorizationReason reason,
            long permissionVersion,
            List<Long> matchedGrantIds,
            Map<String, Object> obligations,
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
                obligations,
                evaluatedAt);
    }

    private static Map<String, Object> buildFieldGrantObligations(
            List<ShareGrant> matchedShareGrants, List<ResolvedMaskStrategy> maskStrategies) {
        // Multiple matched shares may cover the same field. SPEC 38 §82: at
        // equal priority the most restrictive read result wins and deny wins
        // over allow, so merge per field: HIDE > MASK > READ, writable = AND.
        Map<Long, MergedFieldGrant> merged = new LinkedHashMap<>();
        for (ShareGrant grant : matchedShareGrants) {
            for (ShareFieldGrant fg : grant.fieldGrants()) {
                merged.merge(fg.fieldId(),
                        new MergedFieldGrant(fg.fieldId(),
                                resolvePolicy(fg.hidden(), fg.readable(), fg.maskStrategyId()),
                                fg.writable(), fg.maskStrategyId()),
                        MergedFieldGrant::mostRestrictive);
            }
        }
        if (merged.isEmpty()) {
            return Map.of();
        }
        List<Map<String, Object>> fieldGrants = merged.values().stream()
                .map(m -> toFieldGrantEntry(m, maskStrategies))
                .toList();
        return Map.of("fieldGrants", fieldGrants);
    }

    /**
     * Obligations for an ALLOW from role-permission grants: field policies
     * (priority-resolved, then most-restrictive on ties) plus data scopes.
     */
    private static Map<String, Object> buildRoleObligations(
            AuthorizationFacts facts, List<Long> allowGrantIds) {
        Set<Long> allowed = new HashSet<>(allowGrantIds);

        Map<Long, List<ResolvedFieldPolicy>> policiesByField = new LinkedHashMap<>();
        for (ResolvedFieldPolicy policy : facts.roleFieldPolicies()) {
            if (allowed.contains(policy.rolePermissionId())) {
                policiesByField.computeIfAbsent(policy.fieldId(), k -> new ArrayList<>()).add(policy);
            }
        }
        List<Map<String, Object>> fieldGrants = new ArrayList<>();
        for (Map.Entry<Long, List<ResolvedFieldPolicy>> field : policiesByField.entrySet()) {
            int topPriority = field.getValue().stream()
                    .mapToInt(ResolvedFieldPolicy::priority)
                    .max()
                    .orElse(0);
            MergedFieldGrant merged = null;
            for (ResolvedFieldPolicy policy : field.getValue()) {
                if (policy.priority() != topPriority) {
                    continue;
                }
                MergedFieldGrant candidate = new MergedFieldGrant(
                        field.getKey(),
                        resolvePolicy(policy.hidden(), policy.readable(), policy.maskStrategyId()),
                        policy.writable(),
                        policy.maskStrategyId());
                merged = merged == null ? candidate : merged.mostRestrictive(candidate);
            }
            fieldGrants.add(toFieldGrantEntry(merged, facts.maskStrategies()));
        }

        Map<Long, ResolvedDataScope> uniqueScopes = new LinkedHashMap<>();
        for (ResolvedDataScope scope : facts.roleDataScopes()) {
            if (allowed.contains(scope.rolePermissionId())) {
                uniqueScopes.putIfAbsent(scope.dataScopeId(), scope);
            }
        }
        List<Map<String, Object>> dataScopes = uniqueScopes.values().stream()
                .map(DefaultAuthorizationEngine::toDataScopeEntry)
                .toList();

        Map<String, Object> obligations = new LinkedHashMap<>();
        if (!fieldGrants.isEmpty()) {
            obligations.put("fieldGrants", fieldGrants);
        }
        if (!dataScopes.isEmpty()) {
            obligations.put("dataScopes", dataScopes);
        }
        return obligations;
    }

    private static Map<String, Object> toFieldGrantEntry(
            MergedFieldGrant m, List<ResolvedMaskStrategy> maskStrategies) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("fieldId", m.fieldId());
        entry.put("policy", m.policy());
        entry.put("writable", m.writable());
        if (m.maskStrategyId() != null) {
            entry.put("maskStrategyId", m.maskStrategyId());
            // Fail closed: when the referenced strategy is missing, disabled
            // or deleted, the entry carries no type/config and consumers fall
            // back to a conservative generic mask instead of exposing data.
            for (ResolvedMaskStrategy strategy : maskStrategies) {
                if (strategy.strategyId() == m.maskStrategyId()) {
                    entry.put("maskStrategyType", strategy.strategyType());
                    entry.put("maskConfig", strategy.config());
                    break;
                }
            }
        }
        return entry;
    }

    private static Map<String, Object> toDataScopeEntry(ResolvedDataScope scope) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("scopeType", scope.scopeType());
        entry.put("mergeMode", scope.mergeMode());
        if (scope.scopeCode() != null) {
            entry.put("scopeCode", scope.scopeCode());
        }
        if (scope.customPolicyId() != null) {
            entry.put("customPolicyId", scope.customPolicyId());
        }
        if (!scope.teamIds().isEmpty()) {
            entry.put("teamIds", scope.teamIds());
        }
        return entry;
    }

    private record MergedFieldGrant(
            long fieldId, String policy, boolean writable, Long maskStrategyId) {

        private static final Map<String, Integer> READ_RESTRICTIVENESS =
                Map.of("READ", 0, "MASK", 1, "HIDE", 2);

        MergedFieldGrant mostRestrictive(MergedFieldGrant other) {
            boolean otherMoreRestrictive =
                    READ_RESTRICTIVENESS.get(other.policy()) > READ_RESTRICTIVENESS.get(this.policy());
            String policy = otherMoreRestrictive ? other.policy() : this.policy();
            Long maskStrategyId = otherMoreRestrictive
                    ? other.maskStrategyId() : this.maskStrategyId();
            return new MergedFieldGrant(
                    fieldId, policy, writable && other.writable(), maskStrategyId);
        }
    }

    private static String resolvePolicy(boolean hidden, boolean readable, Long maskStrategyId) {
        if (hidden) {
            return "HIDE";
        }
        if (maskStrategyId != null) {
            return "MASK";
        }
        if (readable) {
            return "READ";
        }
        return "HIDE";
    }
}

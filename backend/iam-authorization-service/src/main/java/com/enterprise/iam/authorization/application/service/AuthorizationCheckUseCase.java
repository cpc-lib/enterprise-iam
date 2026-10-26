package com.enterprise.iam.authorization.application.service;

import com.enterprise.iam.authorization.application.port.out.AbacPolicyRepository;
import com.enterprise.iam.authorization.application.port.out.AuthorizationDecisionRecordRepository;
import com.enterprise.iam.authorization.application.port.out.AuthorizationFactRepository;
import com.enterprise.iam.authorization.application.port.out.AuthorizationPlanCache;
import com.enterprise.iam.authorization.application.port.out.ResolvedDataScopeRepository;
import com.enterprise.iam.authorization.application.port.out.ResolvedFieldPolicyRepository;
import com.enterprise.iam.authorization.application.port.out.ResolvedMaskStrategyRepository;
import com.enterprise.iam.authorization.application.port.out.ShareAclProjectionRepository;
import com.enterprise.iam.authorization.domain.model.AbacPolicy;
import com.enterprise.iam.authorization.domain.model.AuthorizationDecision;
import com.enterprise.iam.authorization.domain.model.AuthorizationFacts;
import com.enterprise.iam.authorization.domain.model.AuthorizationRequest;
import com.enterprise.iam.authorization.domain.model.AuthorizationResult;
import com.enterprise.iam.authorization.domain.model.ResolvedDataScope;
import com.enterprise.iam.authorization.domain.model.ResolvedFieldPolicy;
import com.enterprise.iam.authorization.domain.model.ResolvedGrant;
import com.enterprise.iam.authorization.domain.model.ResolvedMaskStrategy;
import com.enterprise.iam.authorization.domain.model.ShareEpoch;
import com.enterprise.iam.authorization.domain.model.ShareFieldGrant;
import com.enterprise.iam.authorization.domain.model.ShareGrant;
import com.enterprise.iam.authorization.domain.model.TeamIds;
import com.enterprise.iam.authorization.domain.service.DefaultAuthorizationEngine;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class AuthorizationCheckUseCase {

    private final AuthorizationFactRepository factRepository;
    private final DefaultAuthorizationEngine engine;
    private final AuthorizationDecisionRecordRepository decisionRecordRepository;
    private final ShareAclProjectionRepository shareAclProjectionRepository;
    private final ResolvedFieldPolicyRepository resolvedFieldPolicyRepository;
    private final ResolvedDataScopeRepository resolvedDataScopeRepository;
    private final ResolvedMaskStrategyRepository resolvedMaskStrategyRepository;
    private final AbacPolicyRepository abacPolicyRepository;
    private final Clock clock;
    private final AuthorizationPlanCache planCache;

    public AuthorizationCheckUseCase(
            AuthorizationFactRepository factRepository,
            DefaultAuthorizationEngine engine,
            AuthorizationDecisionRecordRepository decisionRecordRepository) {
        this(factRepository, engine, decisionRecordRepository, null, null, null, null, null, null, null);
    }

    public AuthorizationCheckUseCase(
            AuthorizationFactRepository factRepository,
            DefaultAuthorizationEngine engine,
            AuthorizationDecisionRecordRepository decisionRecordRepository,
            ShareAclProjectionRepository shareAclProjectionRepository,
            Clock clock) {
        this(factRepository, engine, decisionRecordRepository,
                shareAclProjectionRepository, clock, null, null, null, null, null);
    }

    public AuthorizationCheckUseCase(
            AuthorizationFactRepository factRepository,
            DefaultAuthorizationEngine engine,
            AuthorizationDecisionRecordRepository decisionRecordRepository,
            ShareAclProjectionRepository shareAclProjectionRepository,
            Clock clock,
            AuthorizationPlanCache planCache) {
        this(factRepository, engine, decisionRecordRepository,
                shareAclProjectionRepository, clock, planCache, null, null, null, null);
    }

    public AuthorizationCheckUseCase(
            AuthorizationFactRepository factRepository,
            DefaultAuthorizationEngine engine,
            AuthorizationDecisionRecordRepository decisionRecordRepository,
            ShareAclProjectionRepository shareAclProjectionRepository,
            Clock clock,
            AuthorizationPlanCache planCache,
            ResolvedFieldPolicyRepository resolvedFieldPolicyRepository,
            ResolvedDataScopeRepository resolvedDataScopeRepository) {
        this(factRepository, engine, decisionRecordRepository,
                shareAclProjectionRepository, clock, planCache,
                resolvedFieldPolicyRepository, resolvedDataScopeRepository, null, null);
    }

    public AuthorizationCheckUseCase(
            AuthorizationFactRepository factRepository,
            DefaultAuthorizationEngine engine,
            AuthorizationDecisionRecordRepository decisionRecordRepository,
            ShareAclProjectionRepository shareAclProjectionRepository,
            Clock clock,
            AuthorizationPlanCache planCache,
            ResolvedFieldPolicyRepository resolvedFieldPolicyRepository,
            ResolvedDataScopeRepository resolvedDataScopeRepository,
            ResolvedMaskStrategyRepository resolvedMaskStrategyRepository) {
        this(factRepository, engine, decisionRecordRepository,
                shareAclProjectionRepository, clock, planCache,
                resolvedFieldPolicyRepository, resolvedDataScopeRepository,
                resolvedMaskStrategyRepository, null);
    }

    public AuthorizationCheckUseCase(
            AuthorizationFactRepository factRepository,
            DefaultAuthorizationEngine engine,
            AuthorizationDecisionRecordRepository decisionRecordRepository,
            ShareAclProjectionRepository shareAclProjectionRepository,
            Clock clock,
            AuthorizationPlanCache planCache,
            ResolvedFieldPolicyRepository resolvedFieldPolicyRepository,
            ResolvedDataScopeRepository resolvedDataScopeRepository,
            ResolvedMaskStrategyRepository resolvedMaskStrategyRepository,
            AbacPolicyRepository abacPolicyRepository) {
        this.factRepository = Objects.requireNonNull(factRepository);
        this.engine = Objects.requireNonNull(engine);
        this.decisionRecordRepository = Objects.requireNonNull(decisionRecordRepository);
        this.shareAclProjectionRepository = shareAclProjectionRepository;
        this.clock = clock;
        this.planCache = planCache;
        this.resolvedFieldPolicyRepository = resolvedFieldPolicyRepository;
        this.resolvedDataScopeRepository = resolvedDataScopeRepository;
        this.resolvedMaskStrategyRepository = resolvedMaskStrategyRepository;
        this.abacPolicyRepository = abacPolicyRepository;
    }

    public AuthorizationResult check(AuthorizationRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        if (planCache != null) {
            Optional<AuthorizationResult> cached = planCache.cachedDecision(request);
            if (cached.isPresent()) {
                return cached.get();
            }
        }
        AuthorizationFacts facts = factRepository.loadFacts(request);
        facts = enrichWithShareProjection(request, facts);
        facts = enrichWithRolePolicies(facts);
        facts = enrichWithMaskStrategies(facts);
        facts = enrichWithAbacPolicies(request, facts);
        AuthorizationResult result = engine.evaluate(request, facts);
        decisionRecordRepository.record(result);
        // Only ALLOW outcomes are cached: a fail-closed deny must never be
        // prolonged by a short-lived local cache entry.
        if (planCache != null && result.decision() == AuthorizationDecision.ALLOW) {
            planCache.storeDecision(request, result);
        }
        return result;
    }

    private AuthorizationFacts enrichWithAbacPolicies(
            AuthorizationRequest request, AuthorizationFacts facts) {
        if (!facts.authoritativeAvailable() || abacPolicyRepository == null) {
            return facts;
        }
        String resourceIdKey = String.valueOf(request.resourceId());
        String operationIdKey = String.valueOf(request.operationId());
        Object ctxResourceType = request.context() != null ? request.context().get("resourceType") : null;
        Object ctxOperation = request.context() != null ? request.context().get("operation") : null;
        Map<Long, AbacPolicy> policiesById = new LinkedHashMap<>();
        for (AbacPolicy policy : abacPolicyRepository.findActiveByTenantAndResource(
                request.tenantId(), resourceIdKey, operationIdKey)) {
            policiesById.put(policy.id(), policy);
        }
        if (ctxResourceType instanceof String rt && !rt.isBlank()
                && ctxOperation instanceof String op && !op.isBlank()
                && (!rt.equals(resourceIdKey) || !op.equals(operationIdKey))) {
            for (AbacPolicy policy : abacPolicyRepository.findActiveByTenantAndResource(
                    request.tenantId(), rt.trim(), op.trim())) {
                policiesById.putIfAbsent(policy.id(), policy);
            }
        }
        if (policiesById.isEmpty()) {
            return facts;
        }
        return new AuthorizationFacts(
                true,
                facts.tenantId(),
                facts.resourceId(),
                facts.operationId(),
                facts.authoritativePermissionVersion(),
                facts.resourceOperationEnabled(),
                facts.grants(),
                facts.shareGrants(),
                facts.shareProjectionFresh(),
                facts.expectedShareEpoch(),
                facts.appliedShareEpoch(),
                facts.roleFieldPolicies(),
                facts.roleDataScopes(),
                facts.maskStrategies(),
                new ArrayList<>(policiesById.values()));
    }

    private AuthorizationFacts enrichWithShareProjection(
            AuthorizationRequest request, AuthorizationFacts facts) {
        if (!facts.authoritativeAvailable() || shareAclProjectionRepository == null) {
            return facts;
        }
        List<Long> teamIds = TeamIds.from(request.context());
        List<ShareGrant> shareGrants = shareAclProjectionRepository.findActiveGrants(
                request.tenantId(),
                request.subjectId(),
                teamIds,
                request.resourceId(),
                request.resourceInstanceId(),
                request.operationId(),
                clock == null ? java.time.Instant.now() : clock.instant());
        ShareEpoch epoch = shareAclProjectionRepository.readEpoch(
                request.tenantId(), request.resourceId());
        return new AuthorizationFacts(
                true,
                facts.tenantId(),
                facts.resourceId(),
                facts.operationId(),
                facts.authoritativePermissionVersion(),
                facts.resourceOperationEnabled(),
                facts.grants(),
                shareGrants,
                epoch.fresh(),
                epoch.expectedEpoch(),
                epoch.appliedEpoch(),
                facts.roleFieldPolicies(),
                facts.roleDataScopes(),
                facts.maskStrategies(),
                facts.abacPolicies());
    }

    private AuthorizationFacts enrichWithRolePolicies(AuthorizationFacts facts) {
        if (!facts.authoritativeAvailable()) {
            return facts;
        }
        List<Long> roleGrantIds = new java.util.ArrayList<>();
        List<Long> teamRoleGrantIds = new java.util.ArrayList<>();
        for (ResolvedGrant grant : facts.grants()) {
            if (grant.teamId() == null) {
                roleGrantIds.add(grant.grantId());
            } else {
                teamRoleGrantIds.add(grant.grantId());
            }
        }
        if (roleGrantIds.isEmpty() && teamRoleGrantIds.isEmpty()) {
            return facts;
        }
        List<ResolvedFieldPolicy> fieldPolicies = resolvedFieldPolicyRepository == null
                ? List.of()
                : resolvedFieldPolicyRepository.findForRolePermissions(
                        facts.tenantId(), roleGrantIds, teamRoleGrantIds);
        List<ResolvedDataScope> dataScopes = resolvedDataScopeRepository == null
                ? List.of()
                : resolvedDataScopeRepository.findForRolePermissions(
                        facts.tenantId(), roleGrantIds, teamRoleGrantIds);
        if (fieldPolicies.isEmpty() && dataScopes.isEmpty()) {
            return facts;
        }
        return new AuthorizationFacts(
                true,
                facts.tenantId(),
                facts.resourceId(),
                facts.operationId(),
                facts.authoritativePermissionVersion(),
                facts.resourceOperationEnabled(),
                facts.grants(),
                facts.shareGrants(),
                facts.shareProjectionFresh(),
                facts.expectedShareEpoch(),
                facts.appliedShareEpoch(),
                fieldPolicies,
                dataScopes,
                facts.maskStrategies(),
                facts.abacPolicies());
    }

    /**
     * Loads mask strategy definitions for every non-null maskStrategyId found
     * in both role field policies and share field grants. If the repository is
     * not available or no IDs are referenced, facts are returned unchanged.
     */
    private AuthorizationFacts enrichWithMaskStrategies(AuthorizationFacts facts) {
        if (!facts.authoritativeAvailable() || resolvedMaskStrategyRepository == null) {
            return facts;
        }
        Set<Long> strategyIds = new HashSet<>();
        for (ResolvedFieldPolicy p : facts.roleFieldPolicies()) {
            if (p.maskStrategyId() != null) {
                strategyIds.add(p.maskStrategyId());
            }
        }
        for (ShareGrant sg : facts.shareGrants()) {
            for (ShareFieldGrant fg : sg.fieldGrants()) {
                if (fg.maskStrategyId() != null) {
                    strategyIds.add(fg.maskStrategyId());
                }
            }
        }
        if (strategyIds.isEmpty()) {
            return facts;
        }
        List<ResolvedMaskStrategy> strategies = resolvedMaskStrategyRepository.findByIds(
                facts.tenantId(), strategyIds);
        if (strategies.isEmpty()) {
            return facts;
        }
        return new AuthorizationFacts(
                true,
                facts.tenantId(),
                facts.resourceId(),
                facts.operationId(),
                facts.authoritativePermissionVersion(),
                facts.resourceOperationEnabled(),
                facts.grants(),
                facts.shareGrants(),
                facts.shareProjectionFresh(),
                facts.expectedShareEpoch(),
                facts.appliedShareEpoch(),
                facts.roleFieldPolicies(),
                facts.roleDataScopes(),
                strategies,
                facts.abacPolicies());
    }
}

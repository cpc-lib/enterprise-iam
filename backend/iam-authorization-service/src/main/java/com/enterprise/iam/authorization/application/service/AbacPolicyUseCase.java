package com.enterprise.iam.authorization.application.service;

import com.enterprise.iam.authorization.application.port.out.AbacPolicyRepository;
import com.enterprise.iam.authorization.application.port.out.AuthorizationPlanCache;
import com.enterprise.iam.authorization.application.port.out.PermissionVersionRepository;
import com.enterprise.iam.authorization.application.port.out.PositiveIdGenerator;
import com.enterprise.iam.authorization.domain.model.AbacPolicy;
import com.enterprise.iam.authorization.domain.model.GrantEffect;
import com.enterprise.iam.authorization.domain.service.ConditionDslEvaluator;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * V2.0 Application use case for managing ABAC policies and dry-run DSL evaluation.
 */
public final class AbacPolicyUseCase {

    private final AbacPolicyRepository repository;
    private final PositiveIdGenerator idGenerator;
    private final Clock clock;
    private final PermissionVersionRepository permissionVersionRepository;
    private final AuthorizationPlanCache planCache;

    public AbacPolicyUseCase(
            AbacPolicyRepository repository,
            PositiveIdGenerator idGenerator,
            Clock clock) {
        this(repository, idGenerator, clock, null, null);
    }

    public AbacPolicyUseCase(
            AbacPolicyRepository repository,
            PositiveIdGenerator idGenerator,
            Clock clock,
            PermissionVersionRepository permissionVersionRepository,
            AuthorizationPlanCache planCache) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.permissionVersionRepository = permissionVersionRepository;
        this.planCache = planCache;
    }

    public List<AbacPolicy> listByTenant(long tenantId) {
        if (tenantId <= 0) {
            throw new IllegalArgumentException("tenantId must be positive");
        }
        return repository.findByTenant(tenantId);
    }

    public AbacPolicy create(
            long tenantId,
            String policyCode,
            String policyName,
            String resourceType,
            String operation,
            GrantEffect effect,
            Integer priority,
            String conditionExpr,
            String description) {
        ConditionDslEvaluator.ValidationResult validation = ConditionDslEvaluator.validate(conditionExpr);
        if (!validation.valid()) {
            throw new DomainInvariantException(
                    "Invalid ABAC condition DSL expression: " + validation.detail());
        }
        Instant now = clock.instant();
        AbacPolicy policy = new AbacPolicy(
                idGenerator.nextId(),
                tenantId,
                policyCode,
                policyName,
                resourceType,
                operation,
                effect,
                priority == null ? 100 : priority,
                conditionExpr,
                description,
                AbacPolicy.STATUS_ACTIVE,
                1L,
                now,
                now);
        AbacPolicy saved = repository.save(policy);
        bumpPermissionVersion(tenantId, "ABAC_POLICY_CREATED");
        return saved;
    }

    public AbacPolicy updateStatus(long tenantId, long id, String status) {
        if (tenantId <= 0 || id <= 0) {
            throw new IllegalArgumentException("tenantId and id must be positive");
        }
        String normalized = status == null ? "" : status.trim().toUpperCase();
        if (!AbacPolicy.STATUS_ACTIVE.equals(normalized) && !AbacPolicy.STATUS_DISABLED.equals(normalized)) {
            throw new IllegalArgumentException("status must be ACTIVE or DISABLED");
        }
        boolean updated = repository.updateStatus(tenantId, id, normalized);
        if (!updated) {
            throw new IllegalArgumentException("ABAC policy not found: " + id);
        }
        bumpPermissionVersion(tenantId, "ABAC_POLICY_STATUS_CHANGED");
        return repository.findById(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("ABAC policy not found: " + id));
    }

    public void delete(long tenantId, long id) {
        if (tenantId <= 0 || id <= 0) {
            throw new IllegalArgumentException("tenantId and id must be positive");
        }
        boolean deleted = repository.deleteById(tenantId, id);
        if (!deleted) {
            throw new IllegalArgumentException("ABAC policy not found: " + id);
        }
        bumpPermissionVersion(tenantId, "ABAC_POLICY_DELETED");
    }

    public ConditionDslEvaluator.EvaluationResult evaluateDryRun(
            String conditionExpr, Map<String, Object> attributes) {
        return ConditionDslEvaluator.evaluateDetailed(conditionExpr, attributes);
    }

    private void bumpPermissionVersion(long tenantId, String reason) {
        if (permissionVersionRepository != null) {
            permissionVersionRepository.incrementTenantPermissionVersion(tenantId, reason);
        }
        if (planCache != null) {
            planCache.evictTenant(tenantId);
        }
    }
}

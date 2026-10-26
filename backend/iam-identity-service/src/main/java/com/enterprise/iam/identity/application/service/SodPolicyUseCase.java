package com.enterprise.iam.identity.application.service;

import com.enterprise.iam.identity.application.port.out.PositiveIdGenerator;
import com.enterprise.iam.identity.application.port.out.SodPolicyRepository;
import com.enterprise.iam.identity.domain.model.SodConflictCheckResult;
import com.enterprise.iam.identity.domain.model.SodPolicy;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public final class SodPolicyUseCase {

    private final SodPolicyRepository repository;
    private final PositiveIdGenerator idGenerator;
    private final Clock clock;

    public SodPolicyUseCase(
            SodPolicyRepository repository,
            PositiveIdGenerator idGenerator,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public SodPolicy createPolicy(
            long tenantId,
            String policyCode,
            String policyName,
            String description,
            List<Long> conflictingRoleIds,
            int maxAllowedRoles,
            String enforcementMode,
            String severity) {
        if (policyCode == null || policyCode.isBlank()) {
            throw new IllegalArgumentException("policyCode must not be blank");
        }
        if (policyName == null || policyName.isBlank()) {
            throw new IllegalArgumentException("policyName must not be blank");
        }
        if (conflictingRoleIds == null || conflictingRoleIds.size() < 2) {
            throw new IllegalArgumentException("conflictingRoleIds must contain at least 2 roles");
        }
        String resolvedMode = (enforcementMode == null || enforcementMode.isBlank())
                ? "STATIC"
                : enforcementMode.trim().toUpperCase(Locale.ROOT);
        String resolvedSeverity = (severity == null || severity.isBlank())
                ? "HIGH"
                : severity.trim().toUpperCase(Locale.ROOT);
        Instant now = clock.instant();
        SodPolicy policy = new SodPolicy(
                idGenerator.nextId(),
                tenantId,
                policyCode.trim().toUpperCase(Locale.ROOT),
                policyName.trim(),
                description == null ? "" : description.trim(),
                conflictingRoleIds.stream().distinct().toList(),
                Math.max(1, maxAllowedRoles),
                resolvedMode,
                resolvedSeverity,
                "ACTIVE",
                1L,
                now,
                now);
        return repository.save(policy);
    }

    public List<SodPolicy> listPolicies(long tenantId) {
        return repository.findByTenant(tenantId);
    }

    public SodPolicy toggleStatus(long tenantId, long id, String status) {
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("status must not be blank");
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!"ACTIVE".equals(normalized) && !"DISABLED".equals(normalized)) {
            throw new IllegalArgumentException("status must be ACTIVE or DISABLED");
        }
        Instant now = clock.instant();
        if (!repository.updateStatus(tenantId, id, normalized, now)) {
            throw new IllegalArgumentException("SoD policy not found");
        }
        return repository.findById(tenantId, id)
                .orElseThrow(() -> new IllegalStateException("Updated SoD policy could not be loaded"));
    }

    public SodConflictCheckResult checkRoleConflict(long tenantId, Collection<Long> roleIds) {
        if (roleIds == null || roleIds.size() < 2) {
            return SodConflictCheckResult.pass();
        }
        Set<Long> distinctRoles = new LinkedHashSet<>(roleIds);
        List<SodPolicy> policies = repository.findByTenant(tenantId);
        for (SodPolicy policy : policies) {
            if (!policy.isActiveStaticPolicy()) {
                continue;
            }
            List<Long> matched = policy.conflictingRoleIds().stream()
                    .filter(distinctRoles::contains)
                    .toList();
            if (matched.size() > policy.maxAllowedRoles()) {
                return SodConflictCheckResult.violated(
                        policy.policyCode(),
                        policy.policyName(),
                        matched);
            }
        }
        return SodConflictCheckResult.pass();
    }
}

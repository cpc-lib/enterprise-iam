package com.enterprise.iam.authorization.infrastructure.persistence;

import com.enterprise.iam.authorization.application.port.out.AbacPolicyRepository;
import com.enterprise.iam.authorization.domain.model.AbacPolicy;
import com.enterprise.iam.authorization.domain.model.GrantEffect;
import com.enterprise.iam.authorization.infrastructure.persistence.mapper.AuthorizationPersistenceMapper;
import com.enterprise.iam.authorization.infrastructure.persistence.row.AbacPolicyRow;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class JdbcAbacPolicyRepository implements AbacPolicyRepository {

    private final AuthorizationPersistenceMapper mapper;
    private final Clock clock;

    public JdbcAbacPolicyRepository(AuthorizationPersistenceMapper mapper) {
        this(mapper, Clock.systemUTC());
    }

    public JdbcAbacPolicyRepository(AuthorizationPersistenceMapper mapper, Clock clock) {
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public AbacPolicy save(AbacPolicy policy) {
        Objects.requireNonNull(policy, "policy must not be null");
        Instant now = clock.instant();
        Instant createdAt = policy.createdAt() != null ? policy.createdAt() : now;
        Instant updatedAt = policy.updatedAt() != null ? policy.updatedAt() : now;
        mapper.insertAbacPolicy(
                policy.id(),
                policy.tenantId(),
                policy.policyCode(),
                policy.policyName(),
                policy.resourceType(),
                policy.operation(),
                policy.effect().name(),
                policy.priority(),
                policy.conditionExpr(),
                policy.description(),
                policy.status(),
                policy.version(),
                createdAt,
                updatedAt);
        return policy;
    }

    @Override
    public List<AbacPolicy> findByTenant(long tenantId) {
        return mapper.selectAbacPoliciesByTenant(tenantId).stream()
                .map(JdbcAbacPolicyRepository::toDomain)
                .toList();
    }

    @Override
    public List<AbacPolicy> findActiveByTenantAndResource(
            long tenantId, String resourceType, String operation) {
        return mapper.selectActiveAbacPoliciesByResourceAndOperation(tenantId, resourceType, operation).stream()
                .map(JdbcAbacPolicyRepository::toDomain)
                .toList();
    }

    @Override
    public Optional<AbacPolicy> findById(long tenantId, long id) {
        AbacPolicyRow row = mapper.selectAbacPolicyById(tenantId, id);
        return row == null ? Optional.empty() : Optional.of(toDomain(row));
    }

    @Override
    public boolean updateStatus(long tenantId, long id, String status) {
        return mapper.updateAbacPolicyStatus(tenantId, id, status, clock.instant()) > 0;
    }

    @Override
    public boolean deleteById(long tenantId, long id) {
        return mapper.deleteAbacPolicyById(tenantId, id) > 0;
    }

    private static AbacPolicy toDomain(AbacPolicyRow row) {
        return new AbacPolicy(
                row.id(),
                row.tenantId(),
                row.policyCode(),
                row.policyName(),
                row.resourceType(),
                row.operation(),
                GrantEffect.valueOf(row.effect()),
                row.priority(),
                row.conditionExpr(),
                row.description(),
                row.status(),
                row.version(),
                row.createdAt(),
                row.updatedAt());
    }
}

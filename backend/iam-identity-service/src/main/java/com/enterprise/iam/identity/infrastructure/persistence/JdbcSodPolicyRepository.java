package com.enterprise.iam.identity.infrastructure.persistence;

import com.enterprise.iam.identity.application.port.out.SodPolicyRepository;
import com.enterprise.iam.identity.domain.model.SodPolicy;
import com.enterprise.iam.identity.infrastructure.persistence.mapper.IdentityPersistenceMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public final class JdbcSodPolicyRepository implements SodPolicyRepository {

    private final IdentityPersistenceMapper mapper;

    public JdbcSodPolicyRepository(IdentityPersistenceMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
    }

    @Override
    public SodPolicy save(SodPolicy policy) {
        String json = serializeRoleIds(policy.conflictingRoleIds());
        int rows = mapper.insertSodPolicy(
                policy.id(),
                policy.tenantId(),
                policy.policyCode(),
                policy.policyName(),
                policy.description(),
                json,
                policy.maxAllowedRoles(),
                policy.enforcementMode(),
                policy.severity(),
                policy.status(),
                policy.version(),
                policy.createdAt());
        if (rows != 1) {
            throw new IllegalStateException("Failed to insert SoD policy");
        }
        return policy;
    }

    @Override
    public List<SodPolicy> findByTenant(long tenantId) {
        return mapper.selectSodPoliciesByTenant(tenantId).stream()
                .map(JdbcSodPolicyRepository::toDomain)
                .toList();
    }

    @Override
    public Optional<SodPolicy> findById(long tenantId, long id) {
        return Optional.ofNullable(mapper.selectSodPolicyById(tenantId, id))
                .map(JdbcSodPolicyRepository::toDomain);
    }

    @Override
    public boolean updateStatus(long tenantId, long id, String status, Instant now) {
        return mapper.updateSodPolicyStatus(tenantId, id, status, now) == 1;
    }

    private static SodPolicy toDomain(IdentityPersistenceMapper.SodPolicyRow row) {
        return new SodPolicy(
                row.id(),
                row.tenantId(),
                row.policyCode(),
                row.policyName(),
                row.description(),
                parseRoleIds(row.conflictingRoleIdsJson()),
                row.maxAllowedRoles(),
                row.enforcementMode(),
                row.severity(),
                row.status(),
                row.version(),
                row.createdAt(),
                row.updatedAt());
    }

    private static String serializeRoleIds(List<Long> roleIds) {
        return "[" + roleIds.stream().map(String::valueOf).collect(Collectors.joining(",")) + "]";
    }

    private static List<Long> parseRoleIds(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        String trimmed = json.trim();
        if (trimmed.startsWith("[")) {
            trimmed = trimmed.substring(1);
        }
        if (trimmed.endsWith("]")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (trimmed.isBlank()) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        for (String token : trimmed.split(",")) {
            String cleaned = token.replace("\"", "").trim();
            if (!cleaned.isEmpty()) {
                ids.add(Long.parseUnsignedLong(cleaned));
            }
        }
        return ids;
    }
}

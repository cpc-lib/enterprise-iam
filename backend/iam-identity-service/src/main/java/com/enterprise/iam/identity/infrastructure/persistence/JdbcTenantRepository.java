package com.enterprise.iam.identity.infrastructure.persistence;

import com.enterprise.iam.identity.application.port.out.TenantRepository;
import com.enterprise.iam.identity.domain.model.Tenant;
import com.enterprise.iam.identity.domain.model.TenantStatus;
import com.enterprise.iam.identity.infrastructure.persistence.mapper.IdentityPersistenceMapper;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public class JdbcTenantRepository implements TenantRepository {

    private final IdentityPersistenceMapper mapper;

    public JdbcTenantRepository(IdentityPersistenceMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Tenant save(Tenant tenant) {
        int updated = mapper.insertTenant(
                tenant.id(),
                tenant.tenantCode(),
                tenant.tenantName(),
                tenant.status().name(),
                tenant.version(),
                Instant.now());
        if (updated != 1) {
            throw new IllegalStateException("tenant insert did not affect exactly one row");
        }
        return tenant;
    }

    @Override
    public Optional<Tenant> findById(long tenantId) {
        return Optional.ofNullable(mapper.selectTenantById(tenantId));
    }

    @Override
    public Optional<Tenant> findByCode(String tenantCode) {
        return Optional.ofNullable(mapper.selectTenantByCode(tenantCode));
    }

    @Override
    public java.util.List<Tenant> findAll(String keyword, String status) {
        return mapper.selectTenants(keyword, status);
    }

    @Override
    public void updateStatus(long tenantId, TenantStatus status, Long expectedVersion) {
        Instant now = Instant.now();
        int updated = mapper.updateTenantStatus(
                tenantId,
                status.name(),
                expectedVersion,
                now);
        if (updated != 1) {
            throw new IllegalStateException("tenant status update conflict");
        }
        if (status == TenantStatus.DISABLED) {
            mapper.revokeAllTenantSessions(tenantId, now);
            mapper.revokeAllTenantRefreshTokens(tenantId, now);
        }
    }

    @Override
    public void updateName(long tenantId, String tenantName, Long expectedVersion) {
        int updated = mapper.updateTenantName(
                tenantId,
                tenantName,
                expectedVersion,
                Instant.now());
        if (updated != 1) {
            throw new IllegalStateException("tenant name update conflict");
        }
    }
}

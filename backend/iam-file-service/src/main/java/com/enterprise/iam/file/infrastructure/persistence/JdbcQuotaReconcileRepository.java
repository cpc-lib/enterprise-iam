package com.enterprise.iam.file.infrastructure.persistence;

import com.enterprise.iam.file.application.model.TenantQuotaRow;
import com.enterprise.iam.file.application.port.out.QuotaReconcileRepository;
import com.enterprise.iam.file.infrastructure.persistence.mapper.FilePersistenceMapper;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class JdbcQuotaReconcileRepository implements QuotaReconcileRepository {

    private final FilePersistenceMapper mapper;

    public JdbcQuotaReconcileRepository(FilePersistenceMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public List<TenantQuotaRow> findAll() {
        return mapper.findAllTenantQuotas();
    }

    @Override
    public long sumLogicalUsage(long tenantId) {
        return mapper.sumLogicalUsage(tenantId);
    }

    @Override
    public long sumReservedBytes(long tenantId) {
        return mapper.sumReservedBytes(tenantId);
    }

    @Override
    public int adjustQuota(long tenantId, long usedLogicalBytes, long reservedBytes, long expectedVersion) {
        return mapper.adjustQuota(tenantId, usedLogicalBytes, reservedBytes, expectedVersion, Instant.now());
    }
}

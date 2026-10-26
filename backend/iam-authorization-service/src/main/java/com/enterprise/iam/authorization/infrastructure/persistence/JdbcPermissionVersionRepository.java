package com.enterprise.iam.authorization.infrastructure.persistence;

import com.enterprise.iam.authorization.application.port.out.PermissionVersionRepository;
import com.enterprise.iam.authorization.infrastructure.persistence.mapper.AuthorizationPolicyMapper;

import java.util.Objects;

public final class JdbcPermissionVersionRepository implements PermissionVersionRepository {

    private final AuthorizationPolicyMapper mapper;

    public JdbcPermissionVersionRepository(AuthorizationPolicyMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public long getTenantPermissionVersion(long tenantId) {
        Long version = mapper.selectTenantPermissionVersion(tenantId);
        return version != null ? version : 0L;
    }

    @Override
    public long incrementTenantPermissionVersion(long tenantId, String reason) {
        long id = System.nanoTime();
        mapper.upsertTenantPermissionVersion(id, tenantId, reason);
        Long version = mapper.selectTenantPermissionVersion(tenantId);
        return version != null ? version : 1L;
    }
}

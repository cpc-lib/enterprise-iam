package com.enterprise.iam.authorization.infrastructure.persistence;

import com.enterprise.iam.authorization.application.port.out.DataScopeRepository;
import com.enterprise.iam.authorization.application.port.out.PositiveIdGenerator;
import com.enterprise.iam.authorization.infrastructure.persistence.mapper.AuthorizationPolicyMapper;

import java.time.Instant;
import java.util.Objects;

public final class JdbcDataScopeRepository implements DataScopeRepository {

    private final AuthorizationPolicyMapper mapper;
    private final PositiveIdGenerator idGenerator;

    public JdbcDataScopeRepository(AuthorizationPolicyMapper mapper, PositiveIdGenerator idGenerator) {
        this.mapper = Objects.requireNonNull(mapper);
        this.idGenerator = Objects.requireNonNull(idGenerator);
    }

    @Override
    public long create(long tenantId, String scopeCode, String scopeName, String scopeType, Long customPolicyId) {
        long id = idGenerator.nextId();
        mapper.insertDataScope(
                id, tenantId, scopeCode, scopeName, scopeType, customPolicyId, Instant.now());
        return id;
    }
}

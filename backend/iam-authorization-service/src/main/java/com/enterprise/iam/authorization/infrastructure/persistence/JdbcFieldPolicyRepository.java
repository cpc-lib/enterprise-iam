package com.enterprise.iam.authorization.infrastructure.persistence;

import com.enterprise.iam.authorization.application.port.out.FieldPolicyRepository;
import com.enterprise.iam.authorization.application.port.out.PositiveIdGenerator;
import com.enterprise.iam.authorization.infrastructure.persistence.mapper.AuthorizationPolicyMapper;

import java.time.Instant;
import java.util.Objects;

public final class JdbcFieldPolicyRepository implements FieldPolicyRepository {

    private final AuthorizationPolicyMapper mapper;
    private final PositiveIdGenerator idGenerator;

    public JdbcFieldPolicyRepository(AuthorizationPolicyMapper mapper, PositiveIdGenerator idGenerator) {
        this.mapper = Objects.requireNonNull(mapper);
        this.idGenerator = Objects.requireNonNull(idGenerator);
    }

    @Override
    public long create(long tenantId, long resourceId, long operationId, long fieldId,
                       boolean readable, boolean writable, boolean hidden,
                       Long maskStrategyId, int priority) {
        long id = idGenerator.nextId();
        mapper.insertFieldPolicy(
                id, tenantId, resourceId, operationId, fieldId,
                readable, writable, hidden, maskStrategyId, priority, Instant.now());
        return id;
    }
}

package com.enterprise.iam.authorization.infrastructure.persistence;

import com.enterprise.iam.authorization.application.port.out.PositiveIdGenerator;
import com.enterprise.iam.authorization.application.port.out.RolePermissionFieldPolicyRepository;
import com.enterprise.iam.authorization.infrastructure.persistence.mapper.AuthorizationPolicyMapper;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class JdbcRolePermissionFieldPolicyRepository implements RolePermissionFieldPolicyRepository {

    private final AuthorizationPolicyMapper mapper;
    private final TransactionTemplate transaction;
    private final PositiveIdGenerator idGenerator;

    public JdbcRolePermissionFieldPolicyRepository(
            AuthorizationPolicyMapper mapper,
            PlatformTransactionManager transactionManager,
            PositiveIdGenerator idGenerator) {
        this.mapper = Objects.requireNonNull(mapper);
        this.transaction = new TransactionTemplate(Objects.requireNonNull(transactionManager));
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        this.idGenerator = Objects.requireNonNull(idGenerator);
    }

    @Override
    public void replaceFieldPolicies(long tenantId, long rolePermissionId, List<Long> fieldPolicyIds, long expectedBindingVersion) {
        transaction.executeWithoutResult(status -> {
            Instant now = Instant.now();
            mapper.deleteRoleFieldPolicies(tenantId, rolePermissionId);
            for (Long fieldPolicyId : fieldPolicyIds) {
                long id = idGenerator.nextId();
                mapper.insertRoleFieldPolicy(id, tenantId, rolePermissionId, fieldPolicyId, now);
            }
        });
    }
}

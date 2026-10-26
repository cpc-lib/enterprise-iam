package com.enterprise.iam.authorization.infrastructure.persistence;

import com.enterprise.iam.authorization.application.port.out.RolePermissionRepository;
import com.enterprise.iam.authorization.infrastructure.persistence.mapper.AuthorizationPolicyMapper;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class JdbcRolePermissionRepository implements RolePermissionRepository {

    private final AuthorizationPolicyMapper mapper;
    private final TransactionTemplate transaction;

    public JdbcRolePermissionRepository(
            AuthorizationPolicyMapper mapper, PlatformTransactionManager transactionManager) {
        this.mapper = Objects.requireNonNull(mapper);
        this.transaction = new TransactionTemplate(Objects.requireNonNull(transactionManager));
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
    }

    @Override
    public void replacePermissions(long tenantId, long roleId, List<Long> permissionIds, long expectedRoleVersion) {
        transaction.executeWithoutResult(status -> {
            Instant now = Instant.now();
            mapper.revokeRolePermissions(tenantId, roleId, now);
            for (Long permissionId : permissionIds) {
                long id = System.nanoTime();
                mapper.insertRolePermission(id, tenantId, roleId, permissionId, now);
            }
        });
    }

    @Override
    public List<Long> findActivePermissionIds(long tenantId, long roleId) {
        return mapper.selectActiveRolePermissionIds(tenantId, roleId);
    }
}

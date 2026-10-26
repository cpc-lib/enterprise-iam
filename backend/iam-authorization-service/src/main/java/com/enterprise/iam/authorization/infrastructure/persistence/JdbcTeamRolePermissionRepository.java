package com.enterprise.iam.authorization.infrastructure.persistence;

import com.enterprise.iam.authorization.application.port.out.PositiveIdGenerator;
import com.enterprise.iam.authorization.application.port.out.TeamRolePermissionRepository;
import com.enterprise.iam.authorization.infrastructure.persistence.mapper.AuthorizationPolicyMapper;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class JdbcTeamRolePermissionRepository implements TeamRolePermissionRepository {

    private final AuthorizationPolicyMapper mapper;
    private final TransactionTemplate transaction;
    private final PositiveIdGenerator idGenerator;

    public JdbcTeamRolePermissionRepository(
            AuthorizationPolicyMapper mapper,
            PlatformTransactionManager transactionManager,
            PositiveIdGenerator idGenerator) {
        this.mapper = Objects.requireNonNull(mapper);
        this.transaction = new TransactionTemplate(Objects.requireNonNull(transactionManager));
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        this.idGenerator = Objects.requireNonNull(idGenerator);
    }

    @Override
    public void replacePermissions(long tenantId, long teamRoleId, List<Long> permissionIds, long expectedVersion) {
        transaction.executeWithoutResult(status -> {
            Instant now = Instant.now();
            mapper.revokeTeamRolePermissions(tenantId, teamRoleId, now);
            Long foundTeamId = mapper.selectTeamId(tenantId, teamRoleId);
            long teamId = foundTeamId != null ? foundTeamId : 0L;
            for (Long permissionId : permissionIds) {
                long id = idGenerator.nextId();
                mapper.insertTeamRolePermission(
                        id, tenantId, teamId, teamRoleId, permissionId, now);
            }
        });
    }

    @Override
    public List<Long> findActivePermissionIds(long tenantId, long teamRoleId) {
        return mapper.selectActiveTeamRolePermissionIds(tenantId, teamRoleId);
    }
}

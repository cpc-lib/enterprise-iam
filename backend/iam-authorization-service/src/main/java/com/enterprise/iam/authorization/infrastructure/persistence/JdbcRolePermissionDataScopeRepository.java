package com.enterprise.iam.authorization.infrastructure.persistence;

import com.enterprise.iam.authorization.application.port.out.PositiveIdGenerator;
import com.enterprise.iam.authorization.application.port.out.RolePermissionDataScopeRepository;
import com.enterprise.iam.authorization.application.port.out.RolePermissionDataScopeRepository.DataScopeBindingInput;
import com.enterprise.iam.authorization.infrastructure.persistence.mapper.AuthorizationPolicyMapper;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class JdbcRolePermissionDataScopeRepository implements RolePermissionDataScopeRepository {

    private final AuthorizationPolicyMapper mapper;
    private final TransactionTemplate transaction;
    private final PositiveIdGenerator idGenerator;

    public JdbcRolePermissionDataScopeRepository(
            AuthorizationPolicyMapper mapper,
            PlatformTransactionManager transactionManager,
            PositiveIdGenerator idGenerator) {
        this.mapper = Objects.requireNonNull(mapper);
        this.transaction = new TransactionTemplate(Objects.requireNonNull(transactionManager));
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        this.idGenerator = Objects.requireNonNull(idGenerator);
    }

    @Override
    public void replaceDataScopes(long tenantId, long rolePermissionId, List<DataScopeBindingInput> bindings, long expectedBindingVersion) {
        transaction.executeWithoutResult(status -> {
            Instant now = Instant.now();
            mapper.deleteRoleDataScopeTeams(tenantId, rolePermissionId);
            mapper.deleteRoleDataScopes(tenantId, rolePermissionId);
            for (DataScopeBindingInput binding : bindings) {
                long scopeBindingId = idGenerator.nextId();
                mapper.insertRoleDataScope(
                        scopeBindingId, tenantId, rolePermissionId,
                        binding.dataScopeId(), binding.mergeMode(), now);
                for (Long teamId : binding.teamIds()) {
                    long teamBindingId = idGenerator.nextId();
                    mapper.insertRoleDataScopeTeam(
                            teamBindingId, tenantId, scopeBindingId, teamId,
                            binding.includeChildren(), now);
                }
            }
        });
    }
}

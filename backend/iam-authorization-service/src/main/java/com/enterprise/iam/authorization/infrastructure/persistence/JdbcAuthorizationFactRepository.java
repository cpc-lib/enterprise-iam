package com.enterprise.iam.authorization.infrastructure.persistence;

import com.enterprise.iam.authorization.application.port.out.AuthorizationFactRepository;
import com.enterprise.iam.authorization.domain.model.AuthorizationFacts;
import com.enterprise.iam.authorization.domain.model.AuthorizationRequest;
import com.enterprise.iam.authorization.domain.model.GrantEffect;
import com.enterprise.iam.authorization.domain.model.ResolvedGrant;
import com.enterprise.iam.authorization.infrastructure.persistence.mapper.AuthorizationPolicyMapper;
import com.enterprise.iam.authorization.infrastructure.persistence.row.AuthorizationGrantRow;

import java.util.List;
import java.util.Objects;

/**
 * Loads authorization facts by joining user-role bindings (identity-owned)
 * with role-permission grants (authorization-owned). Phase-01 assumes the
 * authorization datasource can read the identity schema.
 */
public final class JdbcAuthorizationFactRepository implements AuthorizationFactRepository {

    private final AuthorizationPolicyMapper mapper;

    public JdbcAuthorizationFactRepository(AuthorizationPolicyMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public AuthorizationFacts loadFacts(AuthorizationRequest request) {
        try {
            Long version = mapper.selectTenantPermissionVersion(request.tenantId());
            if (version == null) {
                return AuthorizationFacts.unavailable(request);
            }

            Boolean enabled = mapper.selectResourceOperationEnabled(
                    request.tenantId(),
                    request.resourceId(),
                    request.operationId());
            boolean resourceOperationEnabled = Boolean.TRUE.equals(enabled);

            List<ResolvedGrant> roleGrants = mapper.selectRoleGrants(
                    request.tenantId(),
                    request.subjectId(),
                    request.resourceId(),
                    request.operationId()).stream()
                    .map(this::toResolvedGrant)
                    .toList();

            List<ResolvedGrant> teamRoleGrants = mapper.selectTeamRoleGrants(
                    request.tenantId(),
                    request.subjectId(),
                    request.resourceId(),
                    request.operationId()).stream()
                    .map(this::toResolvedGrant)
                    .toList();

            List<ResolvedGrant> grants = new java.util.ArrayList<>(roleGrants.size() + teamRoleGrants.size());
            grants.addAll(roleGrants);
            grants.addAll(teamRoleGrants);

            return new AuthorizationFacts(
                    true,
                    request.tenantId(),
                    request.resourceId(),
                    request.operationId(),
                    version,
                    resourceOperationEnabled,
                    grants);
        } catch (Exception e) {
            return AuthorizationFacts.unavailable(request);
        }
    }

    private ResolvedGrant toResolvedGrant(AuthorizationGrantRow row) {
        if (row.teamId() == null) {
            return new ResolvedGrant(
                    row.grantId(), row.tenantId(), row.subjectId(), row.resourceId(),
                    row.operationId(), GrantEffect.valueOf(row.effect()), row.active(),
                    row.startTime(), row.expireTime());
        }
        return new ResolvedGrant(
                row.grantId(), row.tenantId(), row.subjectId(), row.resourceId(),
                row.operationId(), GrantEffect.valueOf(row.effect()), row.active(),
                row.startTime(), row.expireTime(), row.teamId());
    }
}

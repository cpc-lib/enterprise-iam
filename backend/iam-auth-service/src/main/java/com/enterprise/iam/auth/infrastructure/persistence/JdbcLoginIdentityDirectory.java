package com.enterprise.iam.auth.infrastructure.persistence;

import com.enterprise.iam.auth.application.port.out.LoginIdentityDirectory;
import com.enterprise.iam.auth.domain.model.IdentityType;
import com.enterprise.iam.auth.domain.model.ResolvedLoginIdentity;
import com.enterprise.iam.auth.infrastructure.persistence.mapper.AuthPersistenceMapper;

import java.util.Objects;
import java.util.Optional;

/**
 * Resolves login identity across tenant, user and identity tables.
 * The identity tables are owned by iam-identity-service; the auth service
 * holds read-only access for credential verification.
 */
public final class JdbcLoginIdentityDirectory implements LoginIdentityDirectory {

    private final AuthPersistenceMapper mapper;

    public JdbcLoginIdentityDirectory(AuthPersistenceMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Optional<ResolvedLoginIdentity> findByTenantAndIdentity(
            String tenantCode, IdentityType identityType, String normalizedIdentity) {
        AuthPersistenceMapper.LoginIdentityRow row = mapper.findLoginIdentity(
                tenantCode, identityType.name(), normalizedIdentity);
        return Optional.ofNullable(row).map(identity -> new ResolvedLoginIdentity(
                identity.tenantId(),
                identity.userId(),
                "ACTIVE".equals(identity.userStatus())));
    }
}

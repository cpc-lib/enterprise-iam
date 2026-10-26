package com.enterprise.iam.identity.infrastructure.persistence;

import com.enterprise.iam.identity.application.port.out.UserRoleRepository;
import com.enterprise.iam.identity.domain.model.UserRole;
import com.enterprise.iam.identity.infrastructure.persistence.mapper.IdentityPersistenceMapper;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class JdbcUserRoleRepository implements UserRoleRepository {

    private final IdentityPersistenceMapper mapper;

    public JdbcUserRoleRepository(IdentityPersistenceMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public UserRole save(UserRole userRole) {
        int updated = mapper.insertUserRole(
                userRole.id(),
                userRole.tenantId(),
                userRole.userId(),
                userRole.roleId(),
                userRole.startTime(),
                userRole.expireTime(),
                Instant.now());
        if (updated < 1) {
            throw new IllegalStateException("user_role insert did not affect any rows");
        }
        return userRole;
    }

    @Override
    public Optional<UserRole> findActive(long tenantId, long userId, long roleId) {
        return Optional.ofNullable(mapper.selectActiveUserRole(tenantId, userId, roleId));
    }

    @Override
    public List<UserRole> findActiveByUser(long tenantId, long userId) {
        return mapper.selectActiveUserRolesByUser(tenantId, userId);
    }

    @Override
    public void revoke(long tenantId, long userId, long roleId) {
        mapper.revokeUserRole(tenantId, userId, roleId, Instant.now());
    }
}

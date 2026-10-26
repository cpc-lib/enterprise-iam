package com.enterprise.iam.identity.application.port.out;

import com.enterprise.iam.identity.domain.model.UserRole;

import java.util.List;
import java.util.Optional;

public interface UserRoleRepository {

    UserRole save(UserRole userRole);

    Optional<UserRole> findActive(long tenantId, long userId, long roleId);

    default List<UserRole> findActiveByUser(long tenantId, long userId) {
        return List.of();
    }

    void revoke(long tenantId, long userId, long roleId);
}

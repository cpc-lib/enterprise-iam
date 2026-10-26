package com.enterprise.iam.identity.application.port.out;

import com.enterprise.iam.identity.domain.model.User;
import com.enterprise.iam.identity.domain.model.UserStatus;

import java.util.List;
import java.util.Optional;

public interface UserRepository {

    User save(User user);

    Optional<User> findById(long tenantId, long userId);

    void updateStatus(long tenantId, long userId, UserStatus status, long expectedVersion);

    boolean updateUser(
            long tenantId,
            long userId,
            String displayName,
            String email,
            String phone,
            Long departmentId,
            Long positionId,
            long expectedVersion);

    List<User> listUsers(
            Long tenantId,
            String keyword,
            Long departmentId,
            Long positionId,
            UserStatus status,
            int offset,
            int limit);

    long countUsers(
            Long tenantId,
            String keyword,
            Long departmentId,
            Long positionId,
            UserStatus status);

    List<User> listUsersByRoleId(Long tenantId, long roleId);

    boolean isTenantAdmin(long tenantId, long userId);

    void resetPassword(long tenantId, long userId, String passwordPhc);

    void saveUserIdentity(
            long id,
            long tenantId,
            long userId,
            String identityType,
            String identityKey,
            Long credentialRef,
            String status);

    void initUserPassword(long tenantId, long userId, String rawPassword);

    Long findTenantIdByUserId(long userId);
}

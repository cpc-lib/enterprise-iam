package com.enterprise.iam.identity.application.service;

import com.enterprise.iam.identity.application.port.out.UserRepository;
import com.enterprise.iam.identity.domain.model.User;

import java.util.List;
import java.util.Objects;

public final class GetRoleUsersUseCase {

    private final UserRepository userRepository;

    public GetRoleUsersUseCase(UserRepository userRepository) {
        this.userRepository = Objects.requireNonNull(userRepository);
    }

    public List<User> getUsersByRoleId(Long tenantId, long roleId) {
        return userRepository.listUsersByRoleId(tenantId, roleId);
    }
}

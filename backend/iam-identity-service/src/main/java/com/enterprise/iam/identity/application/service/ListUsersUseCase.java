package com.enterprise.iam.identity.application.service;

import com.enterprise.iam.identity.application.port.out.UserRepository;
import com.enterprise.iam.identity.domain.model.User;
import com.enterprise.iam.identity.domain.model.UserStatus;

import java.util.List;
import java.util.Objects;

public class ListUsersUseCase {

    private final UserRepository userRepository;

    public ListUsersUseCase(UserRepository userRepository) {
        this.userRepository = Objects.requireNonNull(userRepository);
    }

    public PageResult<User> listUsers(
            Long tenantId,
            String keyword,
            Long departmentId,
            Long positionId,
            UserStatus status,
            int page,
            int pageSize) {
        int validPage = Math.max(1, page);
        int validPageSize = pageSize <= 0 ? 20 : Math.min(100, pageSize);
        int offset = (validPage - 1) * validPageSize;

        long total = userRepository.countUsers(tenantId, keyword, departmentId, positionId, status);
        List<User> items = userRepository.listUsers(tenantId, keyword, departmentId, positionId, status, offset, validPageSize);

        return new PageResult<>(items, total, validPage, validPageSize);
    }

    public long countUsers(Long tenantId, UserStatus status) {
        return userRepository.countUsers(tenantId, null, null, null, status);
    }

    public record PageResult<T>(List<T> items, long total, int page, int pageSize) {}
}

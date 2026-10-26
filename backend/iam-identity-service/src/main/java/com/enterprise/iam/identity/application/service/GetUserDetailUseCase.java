package com.enterprise.iam.identity.application.service;

import com.enterprise.iam.identity.application.port.out.UserRepository;
import com.enterprise.iam.identity.domain.model.User;

import java.util.Objects;
import java.util.Optional;

public class GetUserDetailUseCase {

    private final UserRepository userRepository;

    public GetUserDetailUseCase(UserRepository userRepository) {
        this.userRepository = Objects.requireNonNull(userRepository);
    }

    public Optional<User> getDetail(long tenantId, long userId) {
        return userRepository.findById(tenantId, userId);
    }
}

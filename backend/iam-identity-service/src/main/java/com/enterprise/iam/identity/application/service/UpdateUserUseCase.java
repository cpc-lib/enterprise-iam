package com.enterprise.iam.identity.application.service;

import com.enterprise.iam.identity.application.command.UpdateUserCommand;
import com.enterprise.iam.identity.application.port.out.UserRepository;
import com.enterprise.iam.identity.domain.model.User;

import java.util.Objects;

public class UpdateUserUseCase {

    private final UserRepository userRepository;

    public UpdateUserUseCase(UserRepository userRepository) {
        this.userRepository = Objects.requireNonNull(userRepository);
    }

    public User update(UpdateUserCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        User existing = userRepository.findById(command.tenantId(), command.userId())
                .orElseThrow(() -> new IllegalArgumentException("user not found"));

        if (existing.version() != command.expectedVersion()) {
            throw new IllegalStateException("IAM_OPTIMISTIC_LOCK_ERROR: user version mismatch");
        }

        boolean ok = userRepository.updateUser(
                command.tenantId(),
                command.userId(),
                command.displayName(),
                command.email(),
                command.phone(),
                command.departmentId(),
                command.positionId(),
                command.expectedVersion());
        if (!ok) {
            throw new IllegalStateException("failed to update user");
        }

        return userRepository.findById(command.tenantId(), command.userId())
                .orElseThrow(() -> new IllegalStateException("updated user could not be reloaded"));
    }
}
